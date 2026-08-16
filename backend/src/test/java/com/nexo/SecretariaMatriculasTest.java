package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo de trabalho da secretaria sobre matrículas: transições de status com
 * regra de negócio e emissão da declaração em PDF.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-secretaria;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class SecretariaMatriculasTest extends TesteApiBase {

    private long idPorStatus(String bearer, String status) throws Exception {
        JsonNode pagina = json.readTree(mvc.perform(get("/api/matriculas")
                        .param("status", status).param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(pagina.get("content").size())
                .as("o seed precisa ter matrícula " + status)
                .isGreaterThan(0);
        return pagina.get("content").get(0).get("id").asLong();
    }

    @Test
    @DisplayName("secretaria tranca e reativa uma matrícula ativa; transição inválida é 400")
    void transicoesDeStatus() throws Exception {
        String secretaria = bearer("secretaria");
        long id = idPorStatus(secretaria, "ATIVA");

        mvc.perform(patch("/api/matriculas/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TRANCADA\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANCADA"));

        // Repetir o mesmo PATCH não é erro (idempotente).
        mvc.perform(patch("/api/matriculas/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TRANCADA\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/matriculas/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ATIVA\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVA"));

        // ATIVA → PENDENTE não existe no fluxo.
        mvc.perform(patch("/api/matriculas/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDENTE\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("declaração sai em PDF para matrícula ativa e é negada fora disso")
    void declaracaoDeMatricula() throws Exception {
        String secretaria = bearer("secretaria");

        long ativa = idPorStatus(secretaria, "ATIVA");
        byte[] pdf = mvc.perform(get("/api/matriculas/" + ativa + "/declaracao")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        long pendente = idPorStatus(secretaria, "PENDENTE");
        mvc.perform(get("/api/matriculas/" + pendente + "/declaracao")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isBadRequest());
    }
}
