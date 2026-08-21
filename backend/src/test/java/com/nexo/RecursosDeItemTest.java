package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recursos endereçáveis: todo POST que cria algo aponta o Location para um GET que
 * responde, e cada GET de item respeita o mesmo escopo das demais operações sobre
 * aquele recurso — um GET novo sem essa checagem seria um atalho de leitura.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-itens;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class RecursosDeItemTest extends TesteApiBase {

    /** Segue o Location devolvido no 201 e confere que ele resolve. */
    private void locationResolve(String location, String autorizacao) throws Exception {
        assertThat(location).as("o 201 precisa trazer Location").isNotBlank();
        String caminho = location.substring(location.indexOf("/api/"));
        mvc.perform(get(caminho).header(HttpHeaders.AUTHORIZATION, autorizacao))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("cadastro de aluno devolve 201 com Location que resolve")
    void criarAluno() throws Exception {
        String diretor = bearer("diretor");
        JsonNode turmas = json.readTree(mvc.perform(get("/api/turmas")
                .header(HttpHeaders.AUTHORIZATION, diretor)).andReturn().getResponse().getContentAsString());
        long turmaId = turmas.get(0).get("id").asLong();

        String location = mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluno De Teste\",\"turmaId\":" + turmaId + "}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        locationResolve(location, diretor);
    }

    @Test
    @DisplayName("cadastro de professor devolve 201 com Location que resolve")
    void criarProfessor() throws Exception {
        String diretor = bearer("diretor");
        JsonNode materias = json.readTree(mvc.perform(get("/api/materias")
                .header(HttpHeaders.AUTHORIZATION, diretor)).andReturn().getResponse().getContentAsString());
        long materiaId = materias.get(0).get("id").asLong();

        String location = mvc.perform(post("/api/professores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Docente De Teste\",\"dataNascimento\":\"1985-07-02\","
                                + "\"sexo\":\"F\",\"materiaIds\":[" + materiaId + "]}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        locationResolve(location, diretor);
    }

    @Test
    @DisplayName("aviso, questão e observação devolvem 201 com Location que resolve")
    void criarDemaisRecursos() throws Exception {
        String diretor = bearer("diretor");

        String aviso = mvc.perform(post("/api/avisos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Reunião\",\"conteudo\":\"Sexta às 19h\"}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        locationResolve(aviso, diretor);

        String questao = mvc.perform(post("/api/questoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enunciado\":\"Quem proclamou a República?\",\"disciplina\":\"História\"}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        locationResolve(questao, diretor);

        String observacao = mvc.perform(post("/api/alunos/1/observacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Participou bem da aula.\"}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        locationResolve(observacao, diretor);
    }

    @Test
    @DisplayName("GET de item inexistente devolve 404")
    void itemInexistente() throws Exception {
        String diretor = bearer("diretor");
        for (String rota : new String[]{
                "/api/alunos/999999", "/api/professores/999999", "/api/avisos/999999",
                "/api/questoes/999999", "/api/avaliacoes/999999", "/api/mensagens/999999"}) {
            mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, diretor))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("GET de aluno respeita o escopo de turma do professor")
    void escopoDoGetDeAluno() throws Exception {
        String diretor = bearer("diretor");
        String professor = bearer("professor");

        // Acha um aluno de turma que o "professor" não leciona (1º Ano EM A no seed).
        JsonNode turmas = json.readTree(mvc.perform(get("/api/turmas")
                .header(HttpHeaders.AUTHORIZATION, diretor)).andReturn().getResponse().getContentAsString());
        long alheia = -1;
        for (JsonNode t : turmas) {
            if ("1º Ano EM A".equals(t.get("nome").asText())) alheia = t.get("id").asLong();
        }

        JsonNode emRisco = json.readTree(mvc.perform(get("/api/evasao/risco").param("turma", String.valueOf(alheia))
                .header(HttpHeaders.AUTHORIZATION, diretor)).andReturn().getResponse().getContentAsString());
        assertThat(emRisco.size()).as("o seed precisa ter aluno na turma de outro docente").isGreaterThan(0);
        long alunoAlheio = emRisco.get(0).get("alunoId").asLong();

        // O diretor lê; o professor de outra turma, não.
        mvc.perform(get("/api/alunos/" + alunoAlheio).header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alunoAlheio));

        mvc.perform(get("/api/alunos/" + alunoAlheio).header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("aluno não alcança os GETs de item de gestão")
    void alunoNaoLeItensDeGestao() throws Exception {
        String aluno = bearer("aluno");
        for (String rota : new String[]{
                "/api/alunos/1", "/api/professores/1", "/api/questoes/1",
                "/api/avaliacoes/1", "/api/mensagens/1"}) {
            mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, aluno))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("observação de outro aluno não vaza pelo id na URI")
    void observacaoNaoVazaPorId() throws Exception {
        String diretor = bearer("diretor");

        String location = mvc.perform(post("/api/alunos/1/observacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Observação do aluno 1.\"}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        String id = location.substring(location.lastIndexOf('/') + 1);

        // Mesmo id, aluno errado na URI: precisa dar 404, não devolver o conteúdo.
        mvc.perform(get("/api/alunos/2/observacoes/" + id).header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isNotFound());
    }
}
