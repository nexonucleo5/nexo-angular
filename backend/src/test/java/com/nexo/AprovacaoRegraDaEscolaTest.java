package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aprovação de cadastro como controle de verdade: a regra é da escola, não de cada diretor,
 * e quem pede o cadastro não é quem o aprova.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-aprovacao-escola;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class AprovacaoRegraDaEscolaTest extends TesteApiBase {

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    /** Um segundo diretor, para provar que a regra não depende de quem cadastra. */
    @BeforeEach
    void segundoDiretor() {
        if (usuarios.existsByLoginIgnoreCase("diretor2")) return;
        Usuario u = new Usuario();
        u.setLogin("diretor2");
        u.setSenhaHash(encoder.encode(SENHA_PADRAO));
        u.setNome("Segunda Diretora");
        u.setCargo("Direção");
        u.setRole(Role.DIRETOR);
        usuarios.save(u);
    }

    private ResultActions cadastrar(String nome, String bearer) throws Exception {
        long turmaId = json.readTree(mvc.perform(get("/api/turmas").header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        return mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"turmaId\":" + turmaId + "}")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private long pedido(String nome, String bearer) throws Exception {
        return json.readTree(cadastrar(nome, bearer).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions aprovar(long id, String bearer) throws Exception {
        return mvc.perform(post("/api/cadastros-pendentes/" + id + "/aprovar")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private JsonNode fila(String bearer) throws Exception {
        return json.readTree(mvc.perform(get("/api/cadastros-pendentes").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("quem pediu o cadastro não o aprova; outro diretor ou o administrador aprovam")
    void quemPedeNaoAprova() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, true);
        long id = pedido("Paula Pedida Neves", diretor);

        aprovar(id, diretor).andExpect(status().isForbidden());

        // O pedido continua na fila, intacto, e não virou aluno.
        assertThat(fila(diretor).toString()).contains("Paula Pedida Neves");
        assertThat(mvc.perform(get("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor))
                .andReturn().getResponse().getContentAsString()).doesNotContain("Paula Pedida Neves");

        // Outro diretor decide — e o aluno nasce.
        aprovar(id, bearer("diretor2")).andExpect(status().isOk());
        assertThat(mvc.perform(get("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor))
                .andReturn().getResponse().getContentAsString()).contains("Paula Pedida Neves");
    }

    @Test
    @DisplayName("a fila avisa à tela quais pedidos são de quem olha, para não oferecer o botão Aprovar")
    void filaMarcaOsPedidosProprios() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, true);
        pedido("Marcos Marcado Reis", diretor);

        JsonNode paraQuemPediu = fila(diretor);
        JsonNode paraOutro = fila(bearer("diretor2"));

        assertThat(achar(paraQuemPediu, "Marcos Marcado Reis").get("proprio").asBoolean()).isTrue();
        assertThat(achar(paraOutro, "Marcos Marcado Reis").get("proprio").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("quem pediu pode rejeitar o próprio pedido (é só desistir dele)")
    void solicitantePodeRejeitar() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, true);
        long id = pedido("Rui Retirado Lopes", diretor);

        mvc.perform(post("/api/cadastros-pendentes/" + id + "/rejeitar").header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a regra é da escola: o que um diretor liga ou desliga vale para o outro")
    void regraEUmaSoParaAEscola() throws Exception {
        String diretor = bearer("diretor");
        String outro = bearer("diretor2");

        exigirAprovacaoDeAluno(diretor, false);
        try {
            // O outro diretor vê o interruptor desligado — a tela não mente — e cadastra direto.
            assertThat(gestao(outro).get("exigirAprovacaoManualAluno").asBoolean()).isFalse();
            cadastrar("Dino Direto Matos", outro).andExpect(status().isCreated());
        } finally {
            exigirAprovacaoDeAluno(diretor, true);
        }

        // Religada por um, vale para o outro: nada de cadastro direto escapando do controle.
        assertThat(gestao(outro).get("exigirAprovacaoManualAluno").asBoolean()).isTrue();
        cadastrar("Lia Ligada Faria", outro).andExpect(status().isAccepted());
        // E o diretor que religou também passa pela fila.
        cadastrar("Beto Barrado Cruz", diretor).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("mudar a regra da escola deixa rastro de quem mudou e para o quê")
    void mudarARegraEhAuditado() throws Exception {
        String diretor = bearer("diretor");
        exigirAprovacaoDeAluno(diretor, false);
        exigirAprovacaoDeAluno(diretor, true);

        // Lido já decodificado: a API escapa não-ASCII (ç) e o texto cru não casaria.
        JsonNode eventos = json.readTree(mvc.perform(get("/api/auditoria/eventos?tipo=ALTERACAO&size=100")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("content");
        java.util.List<String> acoes = new java.util.ArrayList<>();
        eventos.forEach(e -> acoes.add(e.get("acao").asText()));
        assertThat(acoes).contains("Aprovação de cadastro de aluno deixou de ser exigida",
                "Aprovação de cadastro de aluno passou a ser exigida");
    }

    private JsonNode gestao(String bearer) throws Exception {
        return json.readTree(mvc.perform(get("/api/configuracoes").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("gestao");
    }

    private static JsonNode achar(JsonNode fila, String nome) {
        for (JsonNode p : fila) {
            if (nome.equals(p.get("nome").asText())) return p;
        }
        throw new AssertionError("pedido '" + nome + "' não está na fila");
    }
}
