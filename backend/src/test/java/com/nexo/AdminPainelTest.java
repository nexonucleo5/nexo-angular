package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O painel que substituiu o da secretaria: contas e catálogo de conteúdo.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class AdminPainelTest extends TesteApiBase {

    private JsonNode json(String rota, String bearer) throws Exception {
        return json.readTree(mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // ── Contas ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a listagem de contas mostra acesso, e nada de dado pessoal")
    void contasNaoExpoemPii() throws Exception {
        JsonNode pagina = json("/api/admin/contas", bearer("admin"));
        JsonNode conta = pagina.get("content").get(0);

        // O que a administração precisa para saber de quem é a conta.
        for (String campo : new String[]{"login", "nome", "papel", "ativo"}) {
            assertThat(conta.has(campo)).as("a conta precisa expor " + campo).isTrue();
        }
        // E onde a lista para.
        for (String campo : new String[]{"dataNascimento", "sexo", "endereco", "cpf", "senhaHash"}) {
            assertThat(conta.has(campo)).as("a conta não pode expor " + campo).isFalse();
        }
    }

    @Test
    @DisplayName("desativar uma conta impede o login, e reativar devolve o acesso")
    void desativarContaFechaOAcesso() throws Exception {
        String admin = bearer("admin");
        long alunoId = contaDeLogin("aluno", admin);

        // A conta entra normalmente antes.
        assertThat(loginHttp("aluno", SENHA_PADRAO).getStatus()).isEqualTo(200);

        mvc.perform(patch("/api/admin/contas/" + alunoId + "/ativo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        assertThat(loginHttp("aluno", SENHA_PADRAO).getStatus())
                .as("conta desativada não entra").isEqualTo(401);

        mvc.perform(patch("/api/admin/contas/" + alunoId + "/ativo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        assertThat(loginHttp("aluno", SENHA_PADRAO).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("o administrador não desativa a própria conta")
    void naoSeTrancaDoLadoDeFora() throws Exception {
        String admin = bearer("admin");
        long proprioId = contaDeLogin("admin", admin);

        mvc.perform(patch("/api/admin/contas/" + proprioId + "/ativo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());

        assertThat(loginHttp("admin", SENHA_PADRAO).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a senha provisória redefinida é a que passa a valer")
    void redefinirSenhaProvisoria() throws Exception {
        String admin = bearer("admin");
        long professorId = contaDeLogin("professor", admin);

        JsonNode resposta = json.readTree(mvc.perform(
                        post("/api/admin/contas/" + professorId + "/senha-provisoria")
                                .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String nova = resposta.get("senhaProvisoria").asText();
        assertThat(nova).isNotBlank();

        assertThat(loginHttp("professor", SENHA_PADRAO).getStatus())
                .as("a senha antiga para de valer").isEqualTo(401);
        assertThat(loginHttp("professor", nova).getStatus()).isEqualTo(200);
    }

    // ── Catálogo ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("conteúdo despublicado some da tela do aluno e volta ao ser republicado")
    void publicarEDespublicarConteudo() throws Exception {
        String admin = bearer("admin");
        String aluno = bearer("aluno");

        long materiaId = materiaComConteudo(admin);
        JsonNode conteudos = json("/api/admin/catalogo/materias/" + materiaId + "/conteudos", admin);
        long conteudoId = conteudos.get(0).get("id").asLong();

        int antes = json("/api/materias/" + materiaId + "/conteudos", aluno).size();

        mvc.perform(patch("/api/admin/catalogo/conteudos/" + conteudoId + "/publicado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":false}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        assertThat(json("/api/materias/" + materiaId + "/conteudos", aluno).size())
                .as("o despublicado sai da lista do aluno").isEqualTo(antes - 1);

        // Mas continua no catálogo de quem administra — não foi apagado.
        assertThat(json("/api/admin/catalogo/materias/" + materiaId + "/conteudos", admin).size())
                .isEqualTo(conteudos.size());

        mvc.perform(patch("/api/admin/catalogo/conteudos/" + conteudoId + "/publicado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":true}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        assertThat(json("/api/materias/" + materiaId + "/conteudos", aluno).size()).isEqualTo(antes);
    }

    @Test
    @DisplayName("reordenar exige a lista completa da matéria e aplica a ordem enviada")
    void reordenarConteudos() throws Exception {
        String admin = bearer("admin");
        long materiaId = materiaComConteudo(admin);

        JsonNode atuais = json("/api/admin/catalogo/materias/" + materiaId + "/conteudos", admin);
        List<Long> ids = new ArrayList<>();
        atuais.forEach(c -> ids.add(c.get("id").asLong()));
        assertThat(ids.size()).as("a matéria de teste precisa de ao menos 2 conteúdos").isGreaterThan(1);

        // Lista parcial é recusada: deixaria os ausentes com a ordem antiga.
        mvc.perform(patch("/api/admin/catalogo/materias/" + materiaId + "/ordem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudoIds\":[" + ids.get(0) + "]}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());

        // Invertida, a ordem devolvida é a enviada.
        List<Long> invertida = new ArrayList<>(ids);
        java.util.Collections.reverse(invertida);
        JsonNode depois = json.readTree(mvc.perform(
                        patch("/api/admin/catalogo/materias/" + materiaId + "/ordem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"conteudoIds\":" + invertida + "}")
                                .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        List<Long> resultado = new ArrayList<>();
        depois.forEach(c -> resultado.add(c.get("id").asLong()));
        assertThat(resultado).isEqualTo(invertida);
    }

    @Test
    @DisplayName("desafio despublicado sai do catálogo do aluno e responde 404 no acesso direto")
    void despublicarDesafio() throws Exception {
        String admin = bearer("admin");
        String aluno = bearer("aluno");

        JsonNode doAluno = json("/api/aluno/desafios", aluno);
        assertThat(doAluno.get("desafios").size()).as("o seed precisa de desafios").isGreaterThan(0);
        long desafioId = doAluno.get("desafios").get(0).get("id").asLong();
        int antes = doAluno.get("desafios").size();

        mvc.perform(patch("/api/admin/catalogo/desafios/" + desafioId + "/publicado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":false}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        assertThat(json("/api/aluno/desafios", aluno).get("desafios").size()).isEqualTo(antes - 1);

        // Some da lista não basta: pedir pelo id na mão também não pode entregar.
        mvc.perform(get("/api/aluno/desafios/" + desafioId).header(HttpHeaders.AUTHORIZATION, aluno))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("o painel conta contas e catálogo, e separa o que está fora do ar")
    void dashboard() throws Exception {
        JsonNode d = json("/api/admin/dashboard", bearer("admin"));

        assertThat(d.get("contas").asLong()).isGreaterThan(0);
        assertThat(d.get("alunos").asLong()).isGreaterThan(0);
        assertThat(d.get("admins").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(d.get("materias").asLong()).isGreaterThan(0);
        assertThat(d.has("conteudosDespublicados")).isTrue();
        assertThat(d.has("desafiosDespublicados")).isTrue();
    }

    // ── Apoio ────────────────────────────────────────────────────────────────

    private long contaDeLogin(String login, String admin) throws Exception {
        JsonNode pagina = json("/api/admin/contas?busca=" + login, admin);
        for (JsonNode c : pagina.get("content")) {
            if (login.equalsIgnoreCase(c.get("login").asText())) return c.get("id").asLong();
        }
        throw new AssertionError("conta não encontrada: " + login);
    }

    /** A primeira matéria do catálogo que tenha mais de um conteúdo cadastrado. */
    private long materiaComConteudo(String admin) throws Exception {
        for (JsonNode m : json("/api/admin/catalogo", admin)) {
            if (m.get("conteudos").asInt() > 1) return m.get("id").asLong();
        }
        throw new AssertionError("o seed precisa de uma matéria com ao menos 2 conteúdos");
    }
}
