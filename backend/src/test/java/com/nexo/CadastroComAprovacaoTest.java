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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Exigir aprovação manual de cadastro de aluno": ligada, o cadastro vira um pedido
 * na fila e só a aprovação cria a conta; desligada, o cadastro segue direto.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-cadastro-aprovacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class CadastroComAprovacaoTest extends TesteApiBase {

    @Test
    @DisplayName("com a opção ligada o cadastro não cria conta: vira pedido, sem login nem senha")
    void cadastroViraPedido() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, true);

        JsonNode pedido = json.readTree(cadastrar("Pedro Pendente Alves", diretor)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());

        assertThat(pedido.get("pendente").asBoolean()).isTrue();
        assertThat(pedido.has("senhaProvisoria")).as("não há credencial para uma conta inexistente").isFalse();
        assertThat(pedido.has("emailInstitucional")).isFalse();

        // Não existe aluno algum ainda: nada de ranking, evasão ou lotação de turma.
        assertThat(nomesDeAlunos(diretor)).doesNotContain("Pedro Pendente Alves");
        // E o pedido está na fila, com a contagem que o painel usa.
        assertThat(nomesNaFila(diretor)).contains("Pedro Pendente Alves");
        assertThat(contagem(diretor)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("aprovar cria a conta com acesso, uma única vez, e deixa rastro de quem decidiu")
    void aprovarCriaAConta() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, true);
        long id = pedidoId(cadastrar("Ana Aprovada Costa", diretor), diretor);

        JsonNode criado = json.readTree(mvc.perform(post("/api/cadastros-pendentes/" + id + "/aprovar")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(criado.get("senhaProvisoria").asText()).isNotBlank();
        assertThat(nomesDeAlunos(diretor)).contains("Ana Aprovada Costa");
        assertThat(nomesNaFila(diretor)).doesNotContain("Ana Aprovada Costa");
        // A conta aprovada de fato entra.
        assertThat(autenticar(criado.get("emailInstitucional").asText(),
                criado.get("senhaProvisoria").asText()).get("token").asText()).isNotBlank();

        // Decidir duas vezes não cria o aluno duas vezes.
        mvc.perform(post("/api/cadastros-pendentes/" + id + "/aprovar").header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/cadastros-pendentes/" + id + "/rejeitar").header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isConflict());

        assertThat(eventoDe("Cadastro de aluno aprovado", "Ana Aprovada Costa", bearer("admin"))).isNotNull();
    }

    @Test
    @DisplayName("rejeitar encerra o pedido sem criar aluno nem conta")
    void rejeitarNaoCriaNada() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, true);
        long id = pedidoId(cadastrar("Rita Rejeitada Dias", diretor), diretor);

        mvc.perform(post("/api/cadastros-pendentes/" + id + "/rejeitar").header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isOk());

        assertThat(nomesDeAlunos(diretor)).doesNotContain("Rita Rejeitada Dias");
        assertThat(nomesNaFila(diretor)).doesNotContain("Rita Rejeitada Dias");
        // Rejeitado não volta: aprovar depois não cria a conta.
        mvc.perform(post("/api/cadastros-pendentes/" + id + "/aprovar").header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isConflict());
        assertThat(nomesDeAlunos(diretor)).doesNotContain("Rita Rejeitada Dias");
        assertThat(eventoDe("Cadastro de aluno rejeitado", "Rita Rejeitada Dias", bearer("admin"))).isNotNull();
    }

    @Test
    @DisplayName("com a opção desligada o cadastro segue direto (201) e o administrador nunca passa pela fila")
    void semAprovacaoCriaNaHora() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, false);
        try {
            JsonNode criado = json.readTree(cadastrar("Dora Direta Lima", diretor)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            assertThat(criado.get("senhaProvisoria").asText()).isNotBlank();
        } finally {
            exigirAprovacaoDeAluno(diretor, true);
        }
        // O ADMIN cria a conta direto mesmo com a opção do diretor ligada.
        cadastrar("Adriano Admin Souza", bearer("admin")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a fila é do diretor e do administrador; a opção só aceita verdadeiro ou falso")
    void acessoEValidacao() throws Exception {
        for (String perfil : new String[]{"professor", "aluno"}) {
            mvc.perform(get("/api/cadastros-pendentes").header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/cadastros-pendentes/1/aprovar").header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/cadastros-pendentes/999999/aprovar")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isNotFound());

        // "false" em texto seria lido como ligado: o servidor recusa em vez de enganar o diretor.
        mvc.perform(patch("/api/configuracoes/gestao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exigirAprovacaoManualAluno\":\"false\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isBadRequest());
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions cadastrar(String nome, String bearer) throws Exception {
        long turmaId = json.readTree(mvc.perform(get("/api/turmas").header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        return mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"turmaId\":" + turmaId + "}")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /** O id do pedido recém-criado, lido da própria resposta do cadastro. */
    private long pedidoId(org.springframework.test.web.servlet.ResultActions cadastro, String bearer) throws Exception {
        return json.readTree(cadastro.andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String nomesDeAlunos(String bearer) throws Exception {
        return json.readTree(mvc.perform(get("/api/alunos").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).toString();
    }

    private String nomesNaFila(String bearer) throws Exception {
        return json.readTree(mvc.perform(get("/api/cadastros-pendentes").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).toString();
    }

    private long contagem(String bearer) throws Exception {
        return json.readTree(mvc.perform(get("/api/cadastros-pendentes/contagem")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("pendentes").asLong();
    }

    private JsonNode eventoDe(String acao, String nome, String bearerAdmin) throws Exception {
        JsonNode pagina = json.readTree(mvc.perform(get("/api/auditoria/eventos?tipo=ALTERACAO&size=100&busca={nome}", nome)
                        .header(HttpHeaders.AUTHORIZATION, bearerAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode e : pagina.get("content")) {
            if (acao.equals(e.get("acao").asText())) return e;
        }
        throw new AssertionError("sem evento '" + acao + "' para " + nome);
    }
}
