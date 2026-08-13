package com.nexo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que a aplicação faz quando <b>nenhum perfil</b> é ativado.
 *
 * <p>Este é o cenário que a imagem Docker produzia antes de o Dockerfile fixar
 * {@code SPRING_PROFILES_ACTIVE=prod}: subir sem perfil ligava o console do H2 e
 * semeava três contas com senha conhecida, porque as duas flags vinham ligadas do
 * {@code application.yml}. Todo o endurecimento morava no perfil de produção — um
 * arquivo que só é lido se alguém lembrar de definir uma variável fora do repositório.
 *
 * <p>Os outros testes desta suíte ligam o seed de propósito (ver os
 * {@code @TestPropertySource} das subclasses de {@link TesteApiBase}). Aqui é o
 * contrário: nada é ligado, e o que se verifica é justamente a ausência.
 *
 * <p>De propósito <b>não</b> estende {@link TesteApiBase} — a base pressupõe o seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-padroes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres-abcdef"
})
class PadroesSegurosTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Sem perfil não existe conta nenhuma — nem a de diretor com a senha do
     * {@code DataSeeder}, que é a que dava acesso ao papel mais poderoso do sistema.
     */
    @Test
    void naoSemeiaContasDeDemonstracao() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"diretor\",\"senha\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * O console do H2 fica desligado, e o path é negado — não apenas escondido.
     * O 401 (e não 403) é o esperado para requisição anônima: o {@code denyAll} do
     * SecurityConfig cai no {@code HttpStatusEntryPoint(UNAUTHORIZED)} da cadeia.
     */
    @Test
    void naoExpoeOConsoleDoH2() throws Exception {
        mvc.perform(get("/h2-console"))
                .andExpect(status().isUnauthorized());
    }

    /** A saúde é pública de propósito: é o HEALTHCHECK do contêiner que a consulta. */
    @Test
    void exponeApenasASaudeDoActuator() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        // Qualquer outro endpoint do actuator é negado pela cadeia de segurança,
        // antes mesmo de a lista de exposição do yml entrar na conta.
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isUnauthorized());
    }
}
