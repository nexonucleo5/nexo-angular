package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.domain.Aluno;
import com.nexo.repository.AlunoRepository;
import com.nexo.service.ConfiguracaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os três interruptores de Configurações → Privacidade salvavam no banco e não eram
 * lidos por ninguém: o ranking trazia a escola inteira. Aqui o ponto de vista que
 * importa é o de <b>outro aluno</b> — a única pessoa de quem a privacidade protege no
 * ranking; professor e diretor enxergam o aluno por obrigação do trabalho.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-privacidade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class PrivacidadeAlunoTest extends TesteApiBase {

    private static final String NOME = "Gabriel Mendes"; // aluno do seed, login "aluno"

    @Autowired AlunoRepository alunos;
    @Autowired ConfiguracaoService configuracoes;

    private String observador; // Authorization de outro aluno, criado pelo diretor

    @BeforeEach
    void prepararCenario() throws Exception {
        // O seed vem com a foto vazia e XP baixo: sem estes ajustes o aluno não
        // apareceria entre as 5 posições que o painel devolve, e o teste não provaria nada.
        Aluno gabriel = alunoDoSeed();
        gabriel.setXpTotal(1_000_000);
        gabriel.setFoto("/api/fotos/999");
        alunos.save(gabriel);

        if (observador == null) {
            String diretor = bearer("diretor");
            JsonNode turmas = json.readTree(mvc.perform(get("/api/turmas")
                    .header(HttpHeaders.AUTHORIZATION, diretor)).andReturn().getResponse().getContentAsString());
            JsonNode criado = json.readTree(mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Observador Silva\",\"turmaId\":" + turmas.get(0).get("id").asLong() + "}")
                            .header(HttpHeaders.AUTHORIZATION, diretor))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            observador = "Bearer " + autenticar(criado.get("emailInstitucional").asText(),
                    criado.get("senhaProvisoria").asText()).get("token").asText();
        }
        privacidade(true, true);
    }

    private Aluno alunoDoSeed() {
        return alunos.findAll().stream().filter(a -> NOME.equals(a.getNome())).findFirst().orElseThrow();
    }

    private void privacidade(boolean exibirNoRanking, boolean perfilPublico) throws Exception {
        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exibirNoRanking\":" + exibirNoRanking + ",\"perfilPublico\":" + perfilPublico + "}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isOk());
    }

    private JsonNode painel(String authorization) throws Exception {
        return json.readTree(mvc.perform(get("/api/aluno/dashboard").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode linhaDe(JsonNode painel, String nome) {
        for (JsonNode r : painel.get("ranking")) if (nome.equals(r.get("nome").asText())) return r;
        return null;
    }

    @Test
    @DisplayName("padrão: o aluno aparece para os outros, com foto")
    void padraoAparece() throws Exception {
        JsonNode linha = linhaDe(painel(observador), NOME);

        assertThat(linha).isNotNull();
        assertThat(linha.get("foto").asText()).isEqualTo("/api/fotos/999");
    }

    @Test
    @DisplayName("'Exibir no ranking' desligado tira o aluno do ranking dos outros")
    void foraDoRankingDosOutros() throws Exception {
        privacidade(false, true);

        JsonNode visto = painel(observador);

        assertThat(linhaDe(visto, NOME)).isNull();
        assertThat(visto.get("foraDoRanking").asBoolean()).isFalse(); // o observador segue no ranking
    }

    @Test
    @DisplayName("quem sai do ranking sabe que saiu: sem posição e sem linha própria")
    void quemSaiSabeQueSaiu() throws Exception {
        privacidade(false, true);

        JsonNode proprio = painel(bearer("aluno"));

        assertThat(proprio.get("foraDoRanking").asBoolean()).isTrue();
        assertThat(proprio.get("posicao").asInt()).isZero();
        assertThat(linhaDe(proprio, NOME)).isNull();
    }

    @Test
    @DisplayName("sair do ranking não bagunça a posição de ninguém: a contagem é só de quem participa")
    void totalSoDeQuemParticipa() throws Exception {
        int antes = painel(observador).get("totalAlunos").asInt();

        privacidade(false, true);

        assertThat(painel(observador).get("totalAlunos").asInt()).isEqualTo(antes - 1);
    }

    @Test
    @DisplayName("'Perfil público' desligado esconde a foto dos outros, mas não a do próprio aluno")
    void perfilPrivadoEscondeFoto() throws Exception {
        privacidade(true, false);

        JsonNode paraOutro = linhaDe(painel(observador), NOME);
        JsonNode paraSi = linhaDe(painel(bearer("aluno")), NOME);

        assertThat(paraOutro).isNotNull();                        // segue no ranking, aceitou aparecer
        assertThat(paraOutro.get("foto").isNull()).isTrue();      // mas sem a foto
        assertThat(paraSi.get("foto").asText()).isEqualTo("/api/fotos/999");
    }

    @Test
    @DisplayName("ligar de novo devolve o aluno ao ranking")
    void religarVolta() throws Exception {
        privacidade(false, false);
        privacidade(true, true);

        JsonNode linha = linhaDe(painel(observador), NOME);

        assertThat(linha).isNotNull();
        assertThat(linha.get("foto").asText()).isEqualTo("/api/fotos/999");
    }

    @Test
    @DisplayName("valor que não é booleano é recusado — texto 'false' seria lido como ligado")
    void valorNaoBooleanoRecusado() throws Exception {
        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exibirNoRanking\":\"false\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isBadRequest());

        assertThat(linhaDe(painel(observador), NOME)).isNotNull();
    }

    @Test
    @DisplayName("a chave sem dono saiu: visivelResponsaveis não é mais oferecida")
    void semChaveDeResponsaveis() throws Exception {
        JsonNode cfg = json.readTree(mvc.perform(get("/api/configuracoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andReturn().getResponse().getContentAsString());

        assertThat(cfg.get("privacidade").has("visivelResponsaveis")).isFalse();
    }

    @Test
    @DisplayName("quem desligou antes de o servidor respeitar a chave continua desligado")
    void sincronizacaoDeLegado() throws Exception {
        privacidade(false, true);
        Aluno gabriel = alunoDoSeed();
        gabriel.setExibirNoRanking(true); // simula a linha anterior à coluna: JSON diz false, coluna diz true
        alunos.save(gabriel);

        int ajustados = configuracoes.sincronizarPrivacidadeDosAlunos();

        assertThat(ajustados).isEqualTo(1);
        assertThat(alunoDoSeed().isExibirNoRanking()).isFalse();
        assertThat(configuracoes.sincronizarPrivacidadeDosAlunos()).isZero(); // idempotente
    }
}
