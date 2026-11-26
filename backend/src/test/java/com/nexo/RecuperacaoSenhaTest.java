package com.nexo;

import com.nexo.domain.RedefinicaoSenha;
import com.nexo.repository.RedefinicaoSenhaRepository;
import com.nexo.service.EnvioEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Esqueci minha senha": link de uso único, hash no banco, TTL e queda de todas as sessões. */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-recuperacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.recuperacao.max-pedidos-por-login=3",
        "nexo.recuperacao.max-pedidos-por-ip=50"
})
class RecuperacaoSenhaTest extends TesteApiBase {

    private static final Pattern TOKEN = Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]+)");

    @MockitoBean
    private EnvioEmail email;

    @Autowired
    private RedefinicaoSenhaRepository redefinicoes;

    @Test
    @DisplayName("fluxo completo: link vale uma vez, derruba todas as sessões e aceita a senha nova")
    void fluxoCompleto() throws Exception {
        reset(email);
        // Duas sessões abertas: ambas precisam cair.
        MockHttpServletResponse sessaoA = loginHttp("professor", SENHA_PADRAO);
        MockHttpServletResponse sessaoB = loginHttp("professor", SENHA_PADRAO);
        definirEmail("professor", SENHA_PADRAO, "prof@exemplo.com");

        pedir("professor").andExpect(status().isAccepted());
        String token = tokenEnviadoPara("prof@exemplo.com");

        // O banco guarda só o hash: o valor enviado não pode aparecer em nenhuma coluna.
        assertThat(redefinicoes.findAll()).noneMatch(r -> r.getTokenHash().contains(token));

        // Senha recusada pela política não gasta o único uso do link.
        redefinir(token, "12345678").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SENHA_SEQUENCIAL"));

        redefinir(token, "Cavalo-Azul-77").andExpect(status().isNoContent());

        // Mesmo link de novo: já foi usado.
        redefinir(token, "Outra-Senha-88").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LINK_INVALIDO"));

        // Todas as sessões antigas caíram...
        for (MockHttpServletResponse sessao : new MockHttpServletResponse[]{sessaoA, sessaoB}) {
            mvc.perform(post("/api/auth/refresh").cookie(cookieDeRefresh(sessao)))
                    .andExpect(status().isUnauthorized());
        }
        // ...a senha antiga não entra mais e a nova entra.
        assertThat(loginHttp("professor", SENHA_PADRAO).getStatus()).isEqualTo(401);
        assertThat(loginHttp("professor", "Cavalo-Azul-77").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("conta inexistente e conta sem e-mail respondem igual e não enviam nada")
    void semEnumeracao() throws Exception {
        reset(email);
        String semEmail = pedir("admin").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String inexistente = pedir("ninguem.assim").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(semEmail).isEqualTo(inexistente);
        verify(email, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("um pedido novo invalida o link anterior")
    void soOUltimoPedidoVale() throws Exception {
        reset(email);
        definirEmail("aluno", SENHA_PADRAO, "aluno@exemplo.com");

        pedir("aluno").andExpect(status().isAccepted());
        String primeiro = tokenEnviadoPara("aluno@exemplo.com");
        reset(email);
        pedir("aluno").andExpect(status().isAccepted());
        String segundo = tokenEnviadoPara("aluno@exemplo.com");

        assertThat(segundo).isNotEqualTo(primeiro);
        redefinir(primeiro, "Cavalo-Azul-77").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LINK_INVALIDO"));
        redefinir(segundo, "Cavalo-Azul-77").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("link vencido é recusado")
    void linkVencido() throws Exception {
        reset(email);
        definirEmail("diretor", SENHA_PADRAO, "dir@exemplo.com");
        pedir("diretor").andExpect(status().isAccepted());
        String token = tokenEnviadoPara("dir@exemplo.com");

        // O pedido que acabou de ser criado é o de maior id (a entidade não carrega o usuário fora de sessão).
        RedefinicaoSenha pedido = redefinicoes.findAll().stream()
                .max(java.util.Comparator.comparing(RedefinicaoSenha::getId)).orElseThrow();
        pedido.setExpiraEm(Instant.now().minusSeconds(1));
        redefinicoes.save(pedido);

        redefinir(token, "Cavalo-Azul-77").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LINK_INVALIDO"));
    }

    @Test
    @DisplayName("token inventado é recusado com a mesma resposta de um vencido")
    void tokenInventado() throws Exception {
        redefinir("abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG", "Cavalo-Azul-77")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LINK_INVALIDO"));
    }

    @Test
    @DisplayName("trocar o e-mail de recuperação exige a senha atual e rejeita endereço inválido")
    void emailExigeSenhaAtual() throws Exception {
        String auth = bearer("admin");
        mvc.perform(put("/api/usuarios/me/email-recuperacao").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"errada\",\"email\":\"a@exemplo.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SENHA_ATUAL_INCORRETA"));

        mvc.perform(put("/api/usuarios/me/email-recuperacao").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA_PADRAO + "\",\"email\":\"isso-nao-e-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o pedido repetido para a mesma conta é barrado com 429")
    void limitePorConta() throws Exception {
        for (int i = 0; i < 3; i++) {
            pedir("limite.teste").andExpect(status().isAccepted());
        }
        pedir("limite.teste").andExpect(status().isTooManyRequests());
        verify(email, times(0)).enviar(eq("limite.teste"), anyString(), anyString());
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions pedir(String login) throws Exception {
        return mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions redefinir(String token, String senha) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + senha + "\"}"));
    }

    private void definirEmail(String login, String senha, String endereco) throws Exception {
        String auth = "Bearer " + autenticar(login, senha).get("token").asText();
        mvc.perform(put("/api/usuarios/me/email-recuperacao").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + senha + "\",\"email\":\"" + endereco + "\"}"))
                .andExpect(status().isNoContent());
    }

    /** Lê o link do corpo do e-mail que o serviço mandou enviar e devolve o token. */
    private String tokenEnviadoPara(String destino) {
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq(destino), anyString(), corpo.capture());
        Matcher m = TOKEN.matcher(corpo.getValue());
        assertThat(m.find()).as("o e-mail precisa conter o link de redefinição").isTrue();
        return m.group(1);
    }
}
