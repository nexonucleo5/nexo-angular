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
 * Quem desativou, reativou ou mudou o papel de quem — e o efeito imediato disso nas
 * sessões da conta atingida.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-auditoria-contas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class AuditoriaContasTest extends TesteApiBase {

    @Test
    @DisplayName("desativar e reativar geram evento com operador, conta, papel e origem")
    void desativacaoEhAuditada() throws Exception {
        String admin = bearer("admin");
        long alunoId = contaDeLogin("aluno", admin);

        alterarAtivo(alunoId, false, admin);
        alterarAtivo(alunoId, true, admin);

        JsonNode desativada = eventoDe("Conta desativada", "aluno", admin);
        assertThat(desativada.get("usuario").asText()).isEqualTo("Helena Ramos");
        assertThat(desativada.get("tipo").asText()).isEqualTo("ALTERACAO");
        assertThat(desativada.get("detalhe").asText()).contains("aluno", "ALUNO");
        assertThat(desativada.get("ip").asText()).as("a rede de origem fica registrada").isNotBlank();

        assertThat(eventoDe("Conta reativada", "aluno", admin)).isNotNull();
    }

    @Test
    @DisplayName("desativar derruba na hora o access token já emitido, não só o refresh")
    void desativacaoDerrubaOAccessToken() throws Exception {
        String admin = bearer("admin");
        long professorId = contaDeLogin("professor", admin);
        String tokenDoProfessor = bearer("professor");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenDoProfessor))
                .andExpect(status().isOk());

        alterarAtivo(professorId, false, admin);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenDoProfessor))
                .andExpect(status().isUnauthorized());

        // Reativar não ressuscita o token antigo, mas um login novo funciona.
        alterarAtivo(professorId, true, admin);
        virarOSegundo();
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenDoProfessor))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("promover gera evento com papel anterior e novo, e a conta sai das sessões antigas")
    void promocaoEhAuditadaEDerrubaSessoes() throws Exception {
        String admin = bearer("admin");
        long diretorId = contaDeLogin("diretor", admin);
        var sessaoAntiga = loginHttp("diretor", SENHA_PADRAO);
        String tokenAntigo = "Bearer " + json.readTree(sessaoAntiga.getContentAsString()).get("token").asText();

        try {
            alterarPapel(diretorId, "ADMIN", admin).andExpect(status().isOk());

            JsonNode evento = eventoDe("Papel da conta alterado", "diretor", admin);
            assertThat(evento.get("usuario").asText()).isEqualTo("Helena Ramos");
            assertThat(evento.get("detalhe").asText()).contains("diretor", "DIRETOR", "ADMIN");
            assertThat(evento.get("ip").asText()).isNotBlank();

            // A sessão anterior não carrega o papel velho adiante...
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenAntigo))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/refresh").cookie(cookieDeRefresh(sessaoAntiga)))
                    .andExpect(status().isUnauthorized());
            // ...e o login seguinte já vem com o papel novo.
            assertThat(autenticar("diretor", SENHA_PADRAO).get("usuario").get("role").asText()).isEqualTo("ADMIN");
        } finally {
            alterarPapel(diretorId, "DIRETOR", admin).andExpect(status().isOk());
            virarOSegundo();
        }
        assertThat(autenticar("diretor", SENHA_PADRAO).get("usuario").get("role").asText()).isEqualTo("DIRETOR");
    }

    @Test
    @DisplayName("a troca de papel respeita os limites: só ADMIN, nunca o próprio, nunca aluno/professor")
    void limitesDaTrocaDePapel() throws Exception {
        String admin = bearer("admin");
        long adminId = contaDeLogin("admin", admin);
        long alunoId = contaDeLogin("aluno", admin);
        long diretorId = contaDeLogin("diretor", admin);

        alterarPapel(adminId, "DIRETOR", admin).andExpect(status().isBadRequest());
        alterarPapel(alunoId, "ADMIN", admin).andExpect(status().isBadRequest());
        alterarPapel(diretorId, "ALUNO", admin).andExpect(status().isBadRequest());
        // Quem não é ADMIN não concede nem retira papel, nem o seu próprio.
        alterarPapel(diretorId, "ADMIN", bearer("diretor")).andExpect(status().isForbidden());
        alterarPapel(alunoId, "ADMIN", bearer("professor")).andExpect(status().isForbidden());

        assertThat(autenticar("diretor", SENHA_PADRAO).get("usuario").get("role").asText()).isEqualTo("DIRETOR");
        assertThat(autenticar("admin", SENHA_PADRAO).get("usuario").get("role").asText()).isEqualTo("ADMIN");
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    /**
     * O iat do JWT tem precisão de segundos e a revogação por usuário é inclusiva nesse
     * segundo (errar para o lado seguro). Quem faz login no mesmo segundo da revogação toma
     * 401; o teste espera o segundo virar para não depender de a máquina ser lenta.
     */
    private static void virarOSegundo() throws InterruptedException {
        Thread.sleep(1100);
    }

    private void alterarAtivo(long id, boolean ativo, String bearer) throws Exception {
        mvc.perform(patch("/api/admin/contas/" + id + "/ativo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":" + ativo + "}")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions alterarPapel(long id, String papel, String bearer)
            throws Exception {
        return mvc.perform(patch("/api/admin/contas/" + id + "/papel")
                .contentType(MediaType.APPLICATION_JSON).content("{\"papel\":\"" + papel + "\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private long contaDeLogin(String login, String bearer) throws Exception {
        JsonNode pagina = json.readTree(mvc.perform(get("/api/admin/contas?busca=" + login)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode c : pagina.get("content")) {
            if (login.equalsIgnoreCase(c.get("login").asText())) return c.get("id").asLong();
        }
        throw new AssertionError("conta não encontrada: " + login);
    }

    /** O evento mais recente com a ação dada cujo detalhe cita o login. */
    private JsonNode eventoDe(String acao, String login, String bearer) throws Exception {
        JsonNode pagina = json.readTree(mvc.perform(get("/api/auditoria/eventos?tipo=ALTERACAO&size=100&busca=" + login)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode e : pagina.get("content")) {
            if (acao.equals(e.get("acao").asText())) return e;
        }
        throw new AssertionError("sem evento '" + acao + "' para " + login);
    }
}
