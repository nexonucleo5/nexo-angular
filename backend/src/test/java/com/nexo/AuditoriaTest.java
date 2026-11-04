package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A tela de logs é do administrador e de mais ninguém: a trilha tem nome e IP de quem
 * entrou, então o recorte de acesso é parte do que se testa, não só a listagem.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-auditoria;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class AuditoriaTest extends TesteApiBase {

    private JsonNode eventos(String query) throws Exception {
        return json.readTree(mvc.perform(get("/api/auditoria/eventos" + query)
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("só o administrador lê os logs — nem o diretor, nem professor ou aluno")
    void soOAdministradorLe() throws Exception {
        for (String perfil : new String[]{"diretor", "professor", "aluno"}) {
            mvc.perform(get("/api/auditoria/eventos").header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/auditoria/eventos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lista paginada do mais recente para o mais antigo, com os campos da trilha")
    void listaPaginadaEOrdenada() throws Exception {
        // O próprio login do admin e o do @BeforeEach já geraram eventos.
        JsonNode pagina = eventos("?size=2");

        assertThat(pagina.get("size").asInt()).isEqualTo(2);
        assertThat(pagina.get("content")).hasSize(2);
        assertThat(pagina.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode primeiro = pagina.get("content").get(0);
        for (String campo : new String[]{"id", "usuario", "tipo", "acao", "criadoEm"}) {
            assertThat(primeiro.has(campo)).as("o evento precisa expor " + campo).isTrue();
        }
        assertThat(primeiro.get("criadoEm").asText())
                .isGreaterThanOrEqualTo(pagina.get("content").get(1).get("criadoEm").asText());
    }

    @Test
    @DisplayName("filtra por tipo e por trecho do usuário, ação, detalhe ou IP")
    void filtros() throws Exception {
        loginHttp("aluno", "senha-errada-de-proposito");

        JsonNode erros = eventos("?tipo=ERRO");
        assertThat(erros.get("content")).isNotEmpty();
        erros.get("content").forEach(e -> assertThat(e.get("tipo").asText()).isEqualTo("ERRO"));

        JsonNode porBusca = eventos("?busca=INVÁLIDA");
        assertThat(porBusca.get("content")).as("a busca ignora caixa").isNotEmpty();

        assertThat(eventos("?busca=nada-disso-existe-xyz").get("content")).isEmpty();
    }

    @Test
    @DisplayName("tamanho de página é limitado e período limita a janela")
    void limites() throws Exception {
        assertThat(eventos("?size=100000").get("size").asInt()).isEqualTo(100);
        assertThat(eventos("?page=-3").get("page").asInt()).isZero();
        // Tudo que existe foi criado agora: uma janela de 1 dia o inclui.
        assertThat(eventos("?periodo=1").get("totalElements").asLong()).isPositive();
    }

    @Test
    @DisplayName("senha digitada no campo de login não fica legível na trilha")
    void loginDesconhecidoMascarado() throws Exception {
        String senhaNoCampoErrado = "MinhaSenhaSecreta123";
        loginHttp(senhaNoCampoErrado, "qualquer");

        JsonNode erros = eventos("?tipo=ERRO&size=100");
        boolean achouRastro = false;
        for (JsonNode e : erros.get("content")) {
            assertThat(e.get("usuario").asText()).doesNotContain("SenhaSecreta");
            if (e.get("usuario").asText().equals("mi***(20)")) achouRastro = true;
        }
        assertThat(achouRastro).as("fica só o rastro: 2 letras e o tamanho").isTrue();
    }

    @Test
    @DisplayName("quem consulta a trilha também fica registrado")
    void consultaFicaRegistrada() throws Exception {
        eventos("");
        JsonNode acessos = eventos("?tipo=ACESSO&busca=consultada");
        assertThat(acessos.get("content")).isNotEmpty();
        assertThat(acessos.get("content").get(0).get("usuario").asText()).isNotBlank();
    }

    @Test
    @DisplayName("tipo inexistente é rejeitado em vez de ignorado")
    void tipoInvalido() throws Exception {
        mvc.perform(get("/api/auditoria/eventos?tipo=QUALQUER")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isBadRequest());
    }
}
