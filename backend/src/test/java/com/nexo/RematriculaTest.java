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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rematrícula: renovação do vínculo para o ano seguinte, com promoção de série.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-rematricula;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class RematriculaTest extends TesteApiBase {

    private JsonNode json(String rota, String bearer) throws Exception {
        return json.readTree(mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /**
     * Uma matrícula ATIVA, fora do 3º ano do médio, cujo aluno ainda não foi
     * renovado. Os testes desta classe compartilham o mesmo banco, então escolher
     * "a primeira ativa" fazia o segundo teste cair na matrícula que o primeiro já
     * havia renovado — e tomar o 400 de "já existe matrícula para o ano".
     */
    private JsonNode matriculaRenovavel(String bearer) throws Exception {
        JsonNode todas = json("/api/matriculas?size=200", bearer).get("content");

        java.util.Map<Long, Integer> vinculosPorAluno = new java.util.HashMap<>();
        for (JsonNode m : todas) {
            vinculosPorAluno.merge(m.get("alunoId").asLong(), 1, Integer::sum);
        }

        for (JsonNode m : todas) {
            String turma = m.get("turma").isNull() ? "" : m.get("turma").asText();
            boolean disponivel = "ATIVA".equals(m.get("status").asText())
                    && !turma.isEmpty()
                    && !turma.startsWith("3º Ano EM")
                    && vinculosPorAluno.get(m.get("alunoId").asLong()) == 1;
            if (disponivel) return m;
        }
        throw new AssertionError("o seed precisa de matrícula ativa renovável fora do 3º ano do médio");
    }

    @Test
    @DisplayName("rematrícula promove de série, começa pendente e não repete o ano")
    void renovaIndividual() throws Exception {
        String secretaria = bearer("secretaria");
        JsonNode origem = matriculaRenovavel(secretaria);
        long id = origem.get("id").asLong();
        String turmaAnterior = origem.get("turma").asText();

        JsonNode nova = json.readTree(mvc.perform(post("/api/matriculas/" + id + "/rematricula")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getContentAsString());

        assertThat(nova.get("turmaAnterior").asText()).isEqualTo(turmaAnterior);
        assertThat(nova.get("turmaNova").asText()).isNotEqualTo(turmaAnterior);
        assertThat(nova.get("origemId").asLong()).isEqualTo(id);

        // Rematrícula se confirma, não se presume: nasce pendente.
        mvc.perform(get("/api/matriculas/" + nova.get("matriculaId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        // Renovar de novo o mesmo vínculo é recusado — o ano já tem matrícula.
        mvc.perform(post("/api/matriculas/" + id + "/rematricula")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("documento permanente acompanha o aluno; o que vence volta para a fila")
    void documentosNaRenovacao() throws Exception {
        String secretaria = bearer("secretaria");
        JsonNode origem = matriculaRenovavel(secretaria);
        long id = origem.get("id").asLong();

        // CPF é permanente; comprovante de residência vence todo ano.
        for (String tipo : new String[]{"CPF", "COMPROVANTE_RESIDENCIA"}) {
            mvc.perform(put("/api/matriculas/" + id + "/documentos/" + tipo)
                            .header(HttpHeaders.AUTHORIZATION, secretaria))
                    .andExpect(status().isOk());
        }

        long novaId = json.readTree(mvc.perform(post("/api/matriculas/" + id + "/rematricula")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("matriculaId").asLong();

        JsonNode checklist = json("/api/matriculas/" + novaId + "/documentos/checklist", secretaria);
        for (JsonNode item : checklist.get("itens")) {
            String tipo = item.get("tipo").asText();
            if ("CPF".equals(tipo)) {
                assertThat(item.get("entregue").asBoolean())
                        .as("CPF não muda: tem que acompanhar o aluno").isTrue();
            }
            if ("COMPROVANTE_RESIDENCIA".equals(tipo)) {
                assertThat(item.get("entregue").asBoolean())
                        .as("comprovante vence: precisa ser pedido de novo").isFalse();
            }
        }
    }

    @Test
    @DisplayName("o lote renova quem pode e explica quem ficou de fora")
    void renovaTurmaEmLote() throws Exception {
        String secretaria = bearer("secretaria");

        // 3º Ano EM: turma de concluintes — o lote não pode renovar ninguém dela.
        long turmaConcluinte = -1;
        for (JsonNode t : json("/api/turmas", secretaria)) {
            if ("3º Ano EM A".equals(t.get("nome").asText())) turmaConcluinte = t.get("id").asLong();
        }
        assertThat(turmaConcluinte).isGreaterThan(0);

        JsonNode resultado = json.readTree(mvc.perform(post("/api/matriculas/rematricula")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turmaId\":" + turmaConcluinte + "}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(resultado.get("renovadas").asInt()).isZero();
        for (JsonNode ignorada : resultado.get("semRenovar")) {
            assertThat(ignorada.get("motivo").asText()).contains("Concluinte");
        }

        // Turma sem id existente é 404, e corpo sem turma é 400.
        mvc.perform(post("/api/matriculas/rematricula").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turmaId\":999999}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/matriculas/rematricula").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("matrícula cancelada não é renovada, e quem não é da secretaria não renova nada")
    void limitesDaRenovacao() throws Exception {
        String secretaria = bearer("secretaria");
        JsonNode origem = matriculaRenovavel(secretaria);
        long id = origem.get("id").asLong();

        mvc.perform(patch("/api/matriculas/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADA\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk());

        mvc.perform(post("/api/matriculas/" + id + "/rematricula")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isBadRequest());

        // Renovar vínculo é ato da secretaria/direção — aluno e professor não entram.
        for (String perfil : new String[]{"aluno", "professor"}) {
            mvc.perform(post("/api/matriculas/" + id + "/rematricula")
                            .header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/matriculas/rematricula").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"turmaId\":1}")
                            .header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
        }
    }

    private static org.springframework.test.web.servlet.result.HeaderResultMatchers header() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
    }
}
