package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    /** Faz login e devolve o corpo da resposta (token, refreshToken e usuário). */
    protected JsonNode autenticar(String login, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** Cabeçalho Authorization pronto para o perfil pedido. */
    protected String bearer(String login) throws Exception {
        return "Bearer " + autenticar(login, SENHA_PADRAO).get("token").asText();
    }
}
