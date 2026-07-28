package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.repository.EventoAuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login, bloqueio por tentativas e ciclo de vida do refresh token.
 *
 * <p>Cada teste usa um login diferente de propósito: o bloqueio por tentativas vive
 * num mapa em memória compartilhado pela aplicação, então esgotar as tentativas de um
 * usuário não pode derrubar os outros testes da classe (a ordem entre eles não é
 * garantida pelo JUnit).
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class AutenticacaoTest extends TesteApiBase {

    @Autowired
    private EventoAuditoriaRepository eventos;

    @Test
    @DisplayName("login com credenciais válidas devolve access token e refresh token")
    void loginValido() throws Exception {
        JsonNode corpo = autenticar("diretor", SENHA_PADRAO);
        assertThat(corpo.get("token").asText()).isNotBlank();
        assertThat(corpo.get("refreshToken").asText()).isNotBlank();
        assertThat(corpo.get("usuario").get("role").asText()).isEqualTo("DIRETOR");
    }

    @Test
    @DisplayName("senha errada devolve 401 sem revelar se o login existe")
    void senhaErrada() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"aluno\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        // Login inexistente responde exatamente igual — nada na resposta distingue os dois.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"nao-existe-zzz\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("tentativa inválida é gravada na auditoria apesar da exceção que encerra o request")
    void falhaDeLoginVaiParaAuditoria() throws Exception {
        long antes = eventos.findTop200ByOrderByCriadoEmDesc().stream()
                .filter(e -> "Tentativa de login inválida".equals(e.getAcao()))
                .count();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"auditoria-alvo\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());

        long depois = eventos.findTop200ByOrderByCriadoEmDesc().stream()
                .filter(e -> "Tentativa de login inválida".equals(e.getAcao()))
                .count();

        // O serviço grava o evento e só então lança a exceção. Sem noRollbackFor o
        // Spring desfazia a transação junto e nenhuma falha era registrada.
        assertThat(depois).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("excesso de tentativas bloqueia com 429 e informa Retry-After")
    void bloqueioPorTentativas() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\":\"professor\",\"senha\":\"errada\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"professor\",\"senha\":\"errada\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // Bloqueado é bloqueado: nem com a senha certa passa dentro da janela.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"professor\",\"senha\":\"" + SENHA_PADRAO + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("refresh rotaciona o token e o replay do antigo derruba a sessão inteira")
    void rotacaoEDeteccaoDeReuso() throws Exception {
        String refresh1 = autenticar("aluno", SENHA_PADRAO).get("refreshToken").asText();

        String refresh2 = json.readTree(
                        mvc.perform(post("/api/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString())
                .get("refreshToken").asText();

        assertThat(refresh2).isNotEqualTo(refresh1);

        // Reapresentar o token já rotacionado indica que uma cópia vazou.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isUnauthorized());

        // O ponto da correção: o token paralelo, que ainda era válido, morre junto.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh token desconhecido devolve 401")
    void refreshInvalido() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"nao-existe\"}"))
                .andExpect(status().isUnauthorized());
    }
}
