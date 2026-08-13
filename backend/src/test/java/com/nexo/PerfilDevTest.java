package com.nexo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O outro lado de {@link PadroesSegurosTest}: tornar o padrão seguro só vale se o
 * perfil {@code dev} continuar entregando o que o desenvolvimento precisa. Sem
 * isto, um erro de digitação no {@code application-dev.yml} passaria despercebido
 * até alguém clonar o repositório e encontrar uma aplicação sem dado nenhum, sem
 * pista do motivo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-dev;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres-abcdef"
})
class PerfilDevTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Environment ambiente;

    @Test
    void semeiaAsContasDeDemonstracao() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"diretor\",\"senha\":\"123456\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Verificado pela propriedade, e não por uma requisição: o console do H2 é um
     * servlet registrado à parte, que não passa pelo DispatcherServlet do MockMvc.
     */
    @Test
    void ligaOConsoleDoH2() {
        assertThat(ambiente.getProperty("spring.h2.console.enabled", Boolean.class)).isTrue();
    }
}
