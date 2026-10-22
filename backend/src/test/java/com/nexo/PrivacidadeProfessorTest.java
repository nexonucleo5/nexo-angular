package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.domain.Usuario;
import com.nexo.repository.UsuarioRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Perfil visível para alunos" (Configurações → Privacidade do professor) salvava e não
 * era lido. O único lugar em que o aluno vê o professor é a lista de contatos do chat,
 * então é lá que a chave vale: desligada, o aluno recebe o professor sem a foto.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-privacidade-professor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class PrivacidadeProfessorTest extends TesteApiBase {

    private static final String FOTO = "/api/fotos/888";

    @Autowired UsuarioRepository usuarios;

    private Long professorId;

    @BeforeEach
    void prepararCenario() throws Exception {
        Usuario professor = usuarios.findByLoginIgnoreCase("professor").orElseThrow();
        professor.setFoto(FOTO);
        usuarios.save(professor);
        professorId = professor.getId();
        perfilVisivel(true);
    }

    private void perfilVisivel(boolean visivel) throws Exception {
        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfilVisivelAlunos\":" + visivel + "}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isOk());
    }

    private JsonNode contatoProfessor(String login) throws Exception {
        JsonNode contatos = json.readTree(mvc.perform(get("/api/chat/contatos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode c : contatos) if (c.get("id").asLong() == professorId) return c;
        return null;
    }

    @Test
    @DisplayName("padrão: o aluno vê a foto do professor")
    void padraoComFoto() throws Exception {
        assertThat(contatoProfessor("aluno").get("foto").asText()).isEqualTo(FOTO);
    }

    @Test
    @DisplayName("desligado: o aluno vê o professor sem foto, mas ainda pode falar com ele")
    void desligadoSemFoto() throws Exception {
        perfilVisivel(false);

        JsonNode contato = contatoProfessor("aluno");

        assertThat(contato).isNotNull();
        assertThat(contato.get("foto").isNull()).isTrue();
    }

    @Test
    @DisplayName("a chave só vale para aluno: o diretor segue vendo a foto")
    void diretorSegueVendo() throws Exception {
        perfilVisivel(false);

        assertThat(contatoProfessor("diretor").get("foto").asText()).isEqualTo(FOTO);
    }

    @Test
    @DisplayName("valor que não é booleano é recusado")
    void valorNaoBooleanoRecusado() throws Exception {
        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfilVisivelAlunos\":\"false\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("chaves sem o que controlar saíram: contato de responsáveis e privacidade do diretor")
    void chavesSemDonoSairam() throws Exception {
        JsonNode prof = json.readTree(mvc.perform(get("/api/configuracoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andReturn().getResponse().getContentAsString());
        JsonNode diretor = json.readTree(mvc.perform(get("/api/configuracoes")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andReturn().getResponse().getContentAsString());

        assertThat(prof.get("privacidade").has("exibirContatoResponsaveis")).isFalse();
        assertThat(diretor.has("privacidade")).isFalse();
    }
}
