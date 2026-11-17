package com.nexo;

import com.nexo.service.EnvioEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Passada de QA sobre o que a develop trouxe (recuperação de senha, logs, saúde): cada teste
 * trava uma falha encontrada ali. Banco próprio, porque alguns testes trocam senha e e-mail.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-passada-qa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.recuperacao.max-pedidos-por-login=10",
        "nexo.recuperacao.max-pedidos-por-ip=100"
})
class PassadaQaDevelopTest extends TesteApiBase {

    private static final Pattern TOKEN = Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]+)");

    @MockitoBean
    private EnvioEmail email;

    private void definirEmail(String bearer, String senha, String endereco) throws Exception {
        mvc.perform(put("/api/usuarios/me/email-recuperacao").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + senha + "\",\"email\":\"" + endereco + "\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions redefinir(String token, String senha) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + senha + "\"}"));
    }

    private String pedirELerToken(String login, String destino) throws Exception {
        reset(email);
        mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq(destino), anyString(), corpo.capture());
        Matcher m = TOKEN.matcher(corpo.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("redefinir a senha pelo link derruba também o access token que o invasor já tinha")
    void redefinicaoPorLinkDerrubaOAccessToken() throws Exception {
        String tokenDoInvasor = bearer("professor");
        definirEmail(tokenDoInvasor, SENHA_PADRAO, "prof-qa@exemplo.com");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenDoInvasor))
                .andExpect(status().isOk());

        String link = pedirELerToken("professor", "prof-qa@exemplo.com");
        redefinir(link, "Cavalo-Azul-77").andExpect(status().isNoContent());

        // Antes seguia valendo por até 15 min: tempo de sobra para trocar o e-mail de
        // recuperação e retomar a conta pelo mesmo caminho.
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenDoInvasor))
                .andExpect(status().isUnauthorized());

        // E quem entra com a senha nova não é atingido.
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + autenticar("professor", "Cavalo-Azul-77").get("token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /me/email-recuperacao tem o mesmo limite de erros de senha da troca de senha")
    void emailDeRecuperacaoNaoEhOraculoDeSenha() throws Exception {
        String admin = bearer("admin");
        String corpoErrado = "{\"senhaAtual\":\"errada-errada\",\"email\":\"invasor@exemplo.com\"}";

        for (int i = 0; i < 5; i++) {
            mvc.perform(put("/api/usuarios/me/email-recuperacao").header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON).content(corpoErrado))
                    .andExpect(status().isBadRequest());
        }
        // Com a senha certa, mas já bloqueado: não passa — senão o limite não valeria nada.
        mvc.perform(put("/api/usuarios/me/email-recuperacao").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA_PADRAO + "\",\"email\":\"invasor@exemplo.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("trocar o e-mail de recuperação invalida os links já enviados ao endereço antigo")
    void trocarEmailInvalidaLinkPendente() throws Exception {
        String diretor = bearer("diretor");
        definirEmail(diretor, SENHA_PADRAO, "antigo-qa@exemplo.com");
        String linkNoEmailAntigo = pedirELerToken("diretor", "antigo-qa@exemplo.com");

        definirEmail(diretor, SENHA_PADRAO, "novo-qa@exemplo.com");

        redefinir(linkNoEmailAntigo, "Cavalo-Azul-77").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LINK_INVALIDO"));
    }

    @Test
    @DisplayName("sonda de saúde pública responde UP e não expõe detalhe")
    void saudeResponde() throws Exception {
        mvc.perform(get("/api/saude")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.length()").value(1));
        // Repetida (já em cache) continua igual.
        mvc.perform(get("/api/saude")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("periodo absurdo nas telas de auditoria e logs não vira 500")
    void periodoAbsurdoNaoEstoura() throws Exception {
        String admin = bearer("admin");
        mvc.perform(get("/api/auditoria/eventos?periodo=2147483647").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auditoria/sistema/logs?periodo=2147483647").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("nome de perfil acima do limite é recusado com 400")
    void nomeDePerfilTemTeto() throws Exception {
        mvc.perform(patch("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer("aluno"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "a".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
