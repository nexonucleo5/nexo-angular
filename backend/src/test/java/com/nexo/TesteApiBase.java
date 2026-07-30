package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.security.RefreshTokenCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base dos testes de API: sobe a aplicação inteira contra um H2 em memória e
 * conversa com ela por HTTP, do filtro de JWT ao repositório.
 *
 * <p>Cada classe de teste define o próprio nome de banco em {@code @TestPropertySource}
 * (ver as subclasses), de modo que uma não enxergue o estado da outra — vários testes
 * aqui alteram dados de propósito (senha trocada, sessões revogadas, login bloqueado).
 *
 * <p>De propósito <b>sem</b> {@code @Transactional}: o teste do registro de auditoria
 * verifica justamente que a gravação sobrevive à exceção que encerra a requisição, e
 * uma transação de teste em volta mascararia esse comportamento.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class TesteApiBase {

    protected static final String SENHA_PADRAO = "123456";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @BeforeEach
    void checarSeed() throws Exception {
        // Falha cedo e com mensagem clara se o seed não rodou: sem os três perfis
        // todo o resto quebraria com erros sem relação com o que se quer testar.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"diretor\",\"senha\":\"" + SENHA_PADRAO + "\"}"))
                .andReturn();
    }

    /** Faz login e devolve a resposta HTTP inteira — corpo e o Set-Cookie do refresh. */
    protected MockHttpServletResponse loginHttp(String login, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andReturn().getResponse();
    }

    /** Faz login e devolve o corpo da resposta (access token e usuário). */
    protected JsonNode autenticar(String login, String senha) throws Exception {
        return json.readTree(loginHttp(login, senha).getContentAsString());
    }

    /** Cabeçalho Authorization pronto para o perfil pedido. */
    protected String bearer(String login) throws Exception {
        return "Bearer " + autenticar(login, SENHA_PADRAO).get("token").asText();
    }

    /**
     * O header {@code Set-Cookie} do refresh token. É o único lugar por onde o valor sai
     * do servidor — o corpo da resposta não o carrega —, então os testes de rotação
     * precisam lê-lo daqui.
     */
    protected static String setCookieDeRefresh(MockHttpServletResponse resposta) {
        return resposta.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(RefreshTokenCookie.NOME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "resposta sem o cookie " + RefreshTokenCookie.NOME));
    }

    /** O cookie de refresh da resposta, pronto para reenvio em {@code perform(...).cookie(...)}. */
    protected static Cookie cookieDeRefresh(MockHttpServletResponse resposta) {
        return new Cookie(RefreshTokenCookie.NOME, valorDoSetCookie(setCookieDeRefresh(resposta)));
    }

    /** Valor de um header Set-Cookie, sem os atributos que vêm depois do primeiro ';'. */
    protected static String valorDoSetCookie(String setCookie) {
        String semNome = setCookie.substring(setCookie.indexOf('=') + 1);
        int fimDoValor = semNome.indexOf(';');
        return fimDoValor < 0 ? semNome : semNome.substring(0, fimDoValor);
    }
}
