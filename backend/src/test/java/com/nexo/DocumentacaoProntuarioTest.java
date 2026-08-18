package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checklist de documentos, prontuário e histórico escolar — o trabalho de
 * secretaria que antes não existia ou estava espalhado.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-documentacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class DocumentacaoProntuarioTest extends TesteApiBase {

    private long matriculaId(String bearer) throws Exception {
        JsonNode pagina = json.readTree(mvc.perform(get("/api/matriculas").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return pagina.get("content").get(0).get("id").asLong();
    }

    @Test
    @DisplayName("a situação da documentação passa a ser consequência do checklist")
    void checklistDefineASituacao() throws Exception {
        String secretaria = bearer("secretaria");
        long id = matriculaId(secretaria);

        // Estado inicial: a lista traz todos os tipos, e os faltantes são o
        // roteiro da ligação para o responsável.
        JsonNode inicial = json.readTree(mvc.perform(get("/api/matriculas/" + id + "/documentos/checklist")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(inicial.get("itens").size()).isGreaterThan(0);
        int totalObrigatorios = inicial.get("totalObrigatorios").asInt();
        assertThat(inicial.get("faltantes").size()).isEqualTo(totalObrigatorios);

        // Um documento entregue: sai de INCOMPLETA para PENDENTE sozinho.
        mvc.perform(put("/api/matriculas/" + id + "/documentos/CPF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\":\"cópia simples\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.entregues").value(1));

        // Reenviar o mesmo documento não duplica a entrega.
        mvc.perform(put("/api/matriculas/" + id + "/documentos/CPF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\":\"original conferido\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregues").value(1));

        // Com todos os obrigatórios, a matrícula fica com documentação COMPLETA.
        for (String tipo : new String[]{"CERTIDAO_NASCIMENTO", "COMPROVANTE_RESIDENCIA", "HISTORICO_ESCOLAR"}) {
            mvc.perform(put("/api/matriculas/" + id + "/documentos/" + tipo)
                            .header(HttpHeaders.AUTHORIZATION, secretaria))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/matriculas/" + id + "/documentos/checklist")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(jsonPath("$.situacao").value("COMPLETA"))
                .andExpect(jsonPath("$.faltantes").isEmpty());

        // E a própria matrícula reflete isso, sem ninguém trocar o estado na mão.
        mvc.perform(get("/api/matriculas/" + id).header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(jsonPath("$.documentacao").value("COMPLETA"));

        // Removendo um obrigatório, volta a pendente.
        mvc.perform(delete("/api/matriculas/" + id + "/documentos/CPF")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PENDENTE"));
    }

    @Test
    @DisplayName("prontuário reúne identificação, matrícula, documentos e desempenho")
    void prontuarioReuneTudo() throws Exception {
        String secretaria = bearer("secretaria");

        mvc.perform(get("/api/alunos/1/prontuario").header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificacao.nome").isNotEmpty())
                .andExpect(jsonPath("$.identificacao.idade").isNumber())
                .andExpect(jsonPath("$.matricula.status").isNotEmpty())
                .andExpect(jsonPath("$.documentos.itens").isArray());
    }

    @Test
    @DisplayName("histórico escolar sai em PDF e é restrito a quem administra")
    void historicoEscolar() throws Exception {
        byte[] pdf = mvc.perform(get("/api/alunos/1/historico")
                        .header(HttpHeaders.AUTHORIZATION, bearer("secretaria")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        // Aluno e professor não emitem documento oficial de ninguém.
        for (String perfil : new String[]{"aluno", "professor"}) {
            mvc.perform(get("/api/alunos/1/historico").header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/alunos/1/prontuario").header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
        }
    }
}
