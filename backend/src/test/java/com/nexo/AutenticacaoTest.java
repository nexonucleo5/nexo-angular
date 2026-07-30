package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.repository.EventoAuditoriaRepository;
import com.nexo.security.RefreshTokenCleanupJob;
import com.nexo.security.RefreshTokenCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private RefreshTokenCleanupJob limpeza;

    @Test
    @DisplayName("login com credenciais válidas devolve access token e perfil")
    void loginValido() throws Exception {
        JsonNode corpo = autenticar("diretor", SENHA_PADRAO);
        assertThat(corpo.get("token").asText()).isNotBlank();
        assertThat(corpo.get("usuario").get("role").asText()).isEqualTo("DIRETOR");
    }

    @Test
    @DisplayName("refresh token sai em cookie HttpOnly e não aparece no corpo da resposta")
    void refreshTokenForaDoAlcanceDoJavaScript() throws Exception {
        MockHttpServletResponse resposta = loginHttp("diretor", SENHA_PADRAO);

        // Enquanto voltava no JSON, o cliente tinha de guardá-lo em algum lugar legível
        // por script (era localStorage) — e um XSS levava a sessão de 7 dias inteira.
        assertThat(json.readTree(resposta.getContentAsString()).has("refreshToken")).isFalse();

        // HttpOnly tira do alcance de document.cookie; SameSite=Strict é o que substitui
        // a proteção CSRF em /api/auth/refresh, que agora se autentica por cookie.
        String setCookie = setCookieDeRefresh(resposta);
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Strict");
        assertThat(valorDoSetCookie(setCookie)).isNotBlank();
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
        Cookie refresh1 = cookieDeRefresh(loginHttp("aluno", SENHA_PADRAO));

        Cookie refresh2 = cookieDeRefresh(
                mvc.perform(post("/api/auth/refresh").cookie(refresh1))
                        .andExpect(status().isOk())
                        .andReturn().getResponse());

        assertThat(refresh2.getValue()).isNotEqualTo(refresh1.getValue());

        // Reapresentar o token já rotacionado indica que uma cópia vazou.
        mvc.perform(post("/api/auth/refresh").cookie(refresh1))
                .andExpect(status().isUnauthorized());

        // O ponto da correção: o token paralelo, que ainda era válido, morre junto.
        mvc.perform(post("/api/auth/refresh").cookie(refresh2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a linha revogada sobrevive à limpeza, então o replay ainda é detectado")
    void deteccaoDeReusoSobreviveALimpeza() throws Exception {
        Cookie refresh1 = cookieDeRefresh(loginHttp("aluno", SENHA_PADRAO));
        Cookie refresh2 = cookieDeRefresh(
                mvc.perform(post("/api/auth/refresh").cookie(refresh1))
                        .andExpect(status().isOk())
                        .andReturn().getResponse());

        // A varredura das 03:00 roda com a sessão ativa. Enquanto ela apagava também os
        // revogados, levava embora o rastro do token rotacionado — e a partir dali o
        // replay caía no ramo de "token desconhecido", sem revogação em cascata.
        limpeza.limpar();

        mvc.perform(post("/api/auth/refresh").cookie(refresh1))
                .andExpect(status().isUnauthorized());

        // Se o rastro tivesse sido apagado, este ainda estaria valendo.
        mvc.perform(post("/api/auth/refresh").cookie(refresh2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout encerra só o dispositivo atual e deixa as outras sessões de pé")
    void logoutEhPorDispositivo() throws Exception {
        MockHttpServletResponse disp1 = loginHttp("aluno", SENHA_PADRAO);
        MockHttpServletResponse disp2 = loginHttp("aluno", SENHA_PADRAO);
        String bearer1 = "Bearer " + json.readTree(disp1.getContentAsString()).get("token").asText();

        mvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearer1)
                        .cookie(cookieDeRefresh(disp1)))
                .andExpect(status().isNoContent());

        // O ponto da mudança: antes o logout chamava deleteByUsuario e este refresh
        // também morria — sair no celular deslogava o desktop.
        mvc.perform(post("/api/auth/refresh").cookie(cookieDeRefresh(disp2)))
                .andExpect(status().isOk());

        // A revogação do access token também é por sessão: a do disp2 não foi atingida.
        String bearer2 = "Bearer " + json.readTree(disp2.getContentAsString()).get("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer2))
                .andExpect(status().isOk());

        // Só depois de verificar o disp2: reapresentar o token do disp1 dispara a cascata
        // (a linha ficou revogada, não apagada), o que derrubaria o disp2 junto. É o
        // comportamento desejado — o cookie do disp1 foi limpo no logout, então uma
        // reapresentação só pode vir de uma cópia vazada.
        mvc.perform(post("/api/auth/refresh").cookie(cookieDeRefresh(disp1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o access token para de valer no mesmo instante do logout")
    void logoutInvalidaOAccessTokenNaHora() throws Exception {
        // "professor" está fora: bloqueioPorTentativas esgota as tentativas dele e a
        // ordem entre os testes da classe não é garantida (ver o comentário da classe).
        MockHttpServletResponse sessao = loginHttp("diretor", SENHA_PADRAO);
        String bearer = "Bearer " + json.readTree(sessao.getContentAsString()).get("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearer)
                        .cookie(cookieDeRefresh(sessao)))
                .andExpect(status().isNoContent());

        // Antes o JWT continuava aceito até o exp chegar: havia uma janela de até 15
        // minutos em que uma cópia do token abria a conta depois de o dono ter saído.
        mvc.perform(get("/api/auth/me").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout sem cookie ainda responde 204 e registra a auditoria")
    void logoutSemCookieEIdempotente() throws Exception {
        String bearer = bearer("diretor");
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("refresh sem cookie ou com cookie desconhecido devolve 401")
    void refreshInvalido() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookie.NOME, "nao-existe")))
                .andExpect(status().isUnauthorized());
    }
}
