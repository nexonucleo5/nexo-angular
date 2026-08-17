package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras acadêmicas que antes só existiam como intenção: etapa de ensino do aluno,
 * escopo do professor por matéria, idade plausível e teto de matérias por docente.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-regras;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class RegrasAcademicasTest extends TesteApiBase {

    private JsonNode json(String rota, String bearer) throws Exception {
        return json.readTree(mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // ── Etapa de ensino do aluno ─────────────────────────────────────────────

    @Test
    @DisplayName("aluno só enxerga as matérias da própria etapa, e o catálogo inteiro é do diretor")
    void materiasPorEtapaDoAluno() throws Exception {
        String aluno = bearer("aluno");
        String diretor = bearer("diretor");

        JsonNode catalogo = json("/api/materias", diretor);
        JsonNode doAluno = json("/api/materias", aluno);

        assertThat(doAluno.size())
                .as("o recorte por etapa tem que devolver menos que o catálogo inteiro")
                .isLessThan(catalogo.size());

        // O seed põe o aluno no 2º ano do médio: entram Física/Química/Biologia,
        // sai Ciências, que é exclusiva do fundamental.
        String turma = json("/api/aluno/dashboard", aluno).get("turmaNome").asText();
        assertThat(turma).contains("EM");

        for (JsonNode m : doAluno) {
            assertThat(m.get("segmento").asText())
                    .as("matéria de outra etapa vazou para a lista do aluno: %s", m.get("nome").asText())
                    .isIn("MEDIO", "AMBOS");
        }
        assertThat(doAluno.toString()).doesNotContain("Ciências");
    }

    @Test
    @DisplayName("aluno não abre o conteúdo de matéria de outra etapa nem pedindo pelo id")
    void conteudoDeOutraEtapaEhBarrado() throws Exception {
        String aluno = bearer("aluno");
        String diretor = bearer("diretor");

        long ciencias = -1;
        long fisica = -1;
        for (JsonNode m : json("/api/materias", diretor)) {
            if ("Ciências".equals(m.get("nome").asText())) ciencias = m.get("id").asLong();
            if ("Física".equals(m.get("nome").asText())) fisica = m.get("id").asLong();
        }
        assertThat(ciencias).isGreaterThan(0);

        // Esconder da listagem não basta: o id continua adivinhável.
        mvc.perform(get("/api/materias/" + ciencias + "/conteudos")
                        .header(HttpHeaders.AUTHORIZATION, aluno))
                .andExpect(status().isForbidden());

        // E a matéria da etapa dele segue aberta.
        mvc.perform(get("/api/materias/" + fisica + "/conteudos")
                        .header(HttpHeaders.AUTHORIZATION, aluno))
                .andExpect(status().isOk());
    }

    // ── Escopo do professor por matéria ──────────────────────────────────────

    /** Um aluno de turma que o professor do seed (História) leciona. */
    private long alunoDaTurmaDoProfessor(String professor, String diretor) throws Exception {
        long turmaId = -1;
        for (JsonNode t : json("/api/turmas", diretor)) {
            if ("9º Ano A".equals(t.get("nome").asText())) turmaId = t.get("id").asLong();
        }
        assertThat(turmaId).as("o seed precisa da turma 9º Ano A").isGreaterThan(0);
        JsonNode presencas = json("/api/turmas/" + turmaId + "/frequencia", professor);
        assertThat(presencas.size()).isGreaterThan(0);
        return presencas.get(0).get("alunoId").asLong();
    }

    @Test
    @DisplayName("professor lança nota só da matéria que leciona, mesmo na turma dele")
    void notaSomenteNaPropriaMateria() throws Exception {
        String professor = bearer("professor"); // leciona História
        long alunoId = alunoDaTurmaDoProfessor(professor, bearer("diretor"));

        // Turma certa, matéria alheia: antes passava, porque disciplina era texto livre.
        mvc.perform(patch("/api/alunos/" + alunoId + "/notas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disciplina\":\"Matemática\",\"p1\":9.0}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isForbidden());

        // Sem disciplina também não: virava a gaveta "Geral", fora de qualquer matéria.
        mvc.perform(patch("/api/alunos/" + alunoId + "/notas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"p1\":9.0}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isBadRequest());

        // A dele passa — com acento ou sem, é a mesma matéria.
        mvc.perform(patch("/api/alunos/" + alunoId + "/notas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disciplina\":\"historia\",\"p1\":9.0}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("professor não cria avaliação de matéria que não leciona; diretor cria de qualquer uma")
    void avaliacaoSomenteNaPropriaMateria() throws Exception {
        mvc.perform(post("/api/avaliacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Prova de Química\",\"disciplina\":\"Química\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/avaliacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Prova de História\",\"disciplina\":\"História\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isCreated());

        // O diretor responde pela escola inteira e corrige lançamento errado.
        mvc.perform(post("/api/avaliacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Simulado geral\",\"disciplina\":\"Química\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isCreated());
    }

    // ── Idade e teto de matérias no cadastro ─────────────────────────────────

    private long primeiraTurma(String bearer) throws Exception {
        return json("/api/turmas", bearer).get(0).get("id").asLong();
    }

    @Test
    @DisplayName("cadastro recusa data futura e idade impossível, e aceita a plausível")
    void idadeNoCadastro() throws Exception {
        String diretor = bearer("diretor");
        long turmaId = primeiraTurma(diretor);
        String futuro = LocalDate.now().plusYears(1).toString();
        String ontem = LocalDate.now().minusDays(1).toString();

        for (String data : new String[]{futuro, ontem, "1850-01-01"}) {
            mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Aluno Data Ruim\",\"dataNascimento\":\"" + data + "\","
                                    + "\"sexo\":\"M\",\"turmaId\":" + turmaId + "}")
                            .header(HttpHeaders.AUTHORIZATION, diretor))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluno Data Boa\",\"dataNascimento\":\"2012-08-09\","
                                + "\"sexo\":\"M\",\"turmaId\":" + turmaId + "}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("professor precisa ser maior de idade e leciona no máximo 3 matérias")
    void cadastroDeProfessor() throws Exception {
        String diretor = bearer("diretor");
        JsonNode materias = json("/api/materias", diretor);
        long m1 = materias.get(0).get("id").asLong();

        // Criança não leciona.
        mvc.perform(post("/api/professores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Professor Criança\",\"dataNascimento\":\""
                                + LocalDate.now().minusYears(10) + "\",\"sexo\":\"M\",\"materiaIds\":[" + m1 + "]}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isBadRequest());

        // Nem o catálogo inteiro numa pessoa só.
        StringBuilder todas = new StringBuilder();
        for (JsonNode m : materias) {
            todas.append(todas.isEmpty() ? "" : ",").append(m.get("id").asLong());
        }
        mvc.perform(post("/api/professores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Professor De Tudo\",\"dataNascimento\":\"1985-02-03\","
                                + "\"sexo\":\"F\",\"materiaIds\":[" + todas + "]}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/professores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Professora Valida\",\"dataNascimento\":\"1985-02-03\","
                                + "\"sexo\":\"F\",\"materiaIds\":[" + m1 + "]}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated());
    }
}
