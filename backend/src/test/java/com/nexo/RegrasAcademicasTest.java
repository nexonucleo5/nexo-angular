package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @DisplayName("cadastro de aluno pede só nome e turma, e ignora dado pessoal enviado")
    void cadastroDeAlunoNaoGuardaDadoPessoal() throws Exception {
        String diretor = bearer("diretor");
        long turmaId = primeiraTurma(diretor);

        // Nome e turma bastam: nascimento, sexo e endereço saíram do cadastro.
        String location = mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluno Sem Ficha\",\"turmaId\":" + turmaId + "}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        // Um cliente antigo (ou um atacante) mandando os campos removidos não os
        // planta de volta: o Jackson os descarta e a resposta não os traz.
        JsonNode criado = json.readTree(mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluno Com Ficha\",\"turmaId\":" + turmaId + ","
                                + "\"dataNascimento\":\"2012-08-09\",\"sexo\":\"M\","
                                + "\"endereco\":{\"cep\":\"01310100\",\"cidade\":\"São Paulo\"}}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(criado.has("dataNascimento")).isFalse();
        assertThat(criado.has("sexo")).isFalse();
        assertThat(criado.has("endereco")).isFalse();

        // E o aluno relido também não devolve nada disso.
        JsonNode lido = json(location.substring(location.indexOf("/api/")), diretor);
        assertThat(lido.get("nome").asText()).isEqualTo("Aluno Sem Ficha");
        for (String campo : new String[]{"dataNascimento", "sexo", "endereco", "cpf"}) {
            assertThat(lido.has(campo)).as("o aluno não pode expor " + campo).isFalse();
        }

        // Sem turma o cadastro não passa: é dela que sai o conteúdo do aluno.
        mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluno Sem Turma\"}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isBadRequest());
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

    // ── Progresso por matéria ────────────────────────────────────────────────

    @Test
    @DisplayName("progresso da matéria sai dos conteúdos concluídos e marcar é idempotente")
    void progressoPorMateria() throws Exception {
        String aluno = bearer("aluno");

        JsonNode materias = json("/api/aluno/materias", aluno);
        assertThat(materias.size()).isGreaterThan(0);

        // Uma matéria com conteúdo publicado — é onde o progresso faz sentido.
        JsonNode alvo = null;
        for (JsonNode m : materias) {
            if (m.get("totalConteudos").asInt() > 0) { alvo = m; break; }
        }
        assertThat(alvo).as("o seed precisa de matéria com conteúdo").isNotNull();
        long materiaId = alvo.get("id").asLong();
        assertThat(alvo.get("percentual").asInt()).isZero();

        JsonNode conteudos = json("/api/materias/" + materiaId + "/conteudos", aluno);
        long conteudoId = conteudos.get(0).get("id").asLong();

        // Marcar duas vezes conta uma só — senão o percentual passaria de 100%.
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/aluno/conteudos/" + conteudoId + "/concluido")
                            .header(HttpHeaders.AUTHORIZATION, aluno))
                    .andExpect(status().isNoContent());
        }

        assertThat(json("/api/aluno/materias/" + materiaId + "/concluidos", aluno).size()).isEqualTo(1);

        int percentual = 0;
        for (JsonNode m : json("/api/aluno/materias", aluno)) {
            if (m.get("id").asLong() == materiaId) {
                assertThat(m.get("conteudosConcluidos").asInt()).isEqualTo(1);
                percentual = m.get("percentual").asInt();
            }
        }
        assertThat(percentual).isGreaterThan(0);

        // Desmarcar volta ao zero, e repetir também não é erro.
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/aluno/conteudos/" + conteudoId + "/concluido")
                            .header(HttpHeaders.AUTHORIZATION, aluno))
                    .andExpect(status().isNoContent());
        }
        assertThat(json("/api/aluno/materias/" + materiaId + "/concluidos", aluno).size()).isZero();
    }

    @Test
    @DisplayName("aluno não conclui conteúdo de matéria fora da etapa dele")
    void progressoRespeitaAEtapa() throws Exception {
        String aluno = bearer("aluno");
        long ciencias = -1;
        for (JsonNode m : json("/api/materias", bearer("diretor"))) {
            if ("Ciências".equals(m.get("nome").asText())) ciencias = m.get("id").asLong();
        }
        mvc.perform(get("/api/aluno/materias/" + ciencias + "/concluidos")
                        .header(HttpHeaders.AUTHORIZATION, aluno))
                .andExpect(status().isForbidden());
    }

    // ── Escopo das telas do professor ────────────────────────────────────────

    @Test
    @DisplayName("professor vê só as turmas que leciona e só as matérias dele")
    void telasDoProfessorVemRecortadas() throws Exception {
        String professor = bearer("professor"); // leciona História, em 3 turmas
        String diretor = bearer("diretor");

        JsonNode todas = json("/api/turmas", diretor);
        JsonNode minhas = json("/api/turmas", professor);

        assertThat(minhas.size())
                .as("o seletor do diário e o de notas listavam a escola inteira")
                .isLessThan(todas.size());
        assertThat(minhas.size()).isGreaterThan(0);

        // Toda turma listada tem que ser acionável: antes, escolher a turma de um
        // colega só rendia 403 no primeiro clique seguinte.
        for (JsonNode t : minhas) {
            mvc.perform(get("/api/turmas/" + t.get("id").asLong() + "/frequencia")
                            .header(HttpHeaders.AUTHORIZATION, professor))
                    .andExpect(status().isOk());
        }

        JsonNode materias = json("/api/professor/materias", professor);
        assertThat(materias.size()).isEqualTo(1);
        assertThat(materias.get(0).asText()).isEqualTo("História");
    }

    @Test
    @DisplayName("a lista de alunos chega recortada pelas turmas do professor")
    void listaDeAlunosDoProfessor() throws Exception {
        String professor = bearer("professor");
        String diretor = bearer("diretor");

        // O professor conseguir a lista é o ponto: antes a tela de comunicação
        // pedia /api/matriculas, que é do diretor, e ele tomava 403 em silêncio.
        JsonNode meus = json("/api/alunos", professor);
        JsonNode todos = json("/api/alunos", diretor);

        assertThat(meus.size()).isGreaterThan(0);
        assertThat(meus.size())
                .as("professor não pode ver a escola inteira")
                .isLessThan(todos.size());

        // Só alunos das turmas que ele leciona, e nenhuma outra.
        java.util.Set<String> turmasDele = new java.util.HashSet<>();
        for (JsonNode t : json("/api/turmas", professor)) {
            turmasDele.add(t.get("nome").asText());
        }
        for (JsonNode a : meus) {
            assertThat(turmasDele).contains(a.get("turma").asText());
        }

        // A coleção não entrega o login de toda a turma de uma vez.
        assertThat(meus.get(0).has("emailInstitucional")).isFalse();

        // Aluno não alcança a lista de jeito nenhum.
        mvc.perform(get("/api/alunos").header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isForbidden());
    }
}
