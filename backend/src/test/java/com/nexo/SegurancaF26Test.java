package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.repository.AccessTokenRevogadoRepository;
import com.nexo.security.AccessTokensRevogados;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correções da rodada de segurança F26: cada teste trava uma brecha apontada no relatório
 * de QA (ou achada na varredura posterior) para que ela não volte sem alguém perceber.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-f26;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class SegurancaF26Test extends TesteApiBase {

    @Autowired
    private AccessTokensRevogados revogados;

    @Autowired
    private AccessTokenRevogadoRepository repositorioRevogados;

    private long idDe(String login) throws Exception {
        return autenticar(login, SENHA_PADRAO).get("usuario").get("id").asLong();
    }

    private JsonNode obter(String rota, String bearer) throws Exception {
        return json.readTree(mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // ── VULN-01: histórico do chat ───────────────────────────────────────────

    @Test
    @DisplayName("histórico do chat só abre para quem poderia trocar mensagem com o par")
    void historicoDoChatRespeitaOsPares() throws Exception {
        String aluno = bearer("aluno");
        long professor = idDe("professor");
        long diretor = idDe("diretor");
        long admin = idDe("admin");
        long eu = idDe("aluno");

        // Aluno ↔ professor e aluno ↔ diretor são pares válidos.
        mvc.perform(get("/api/chat/" + professor).header(HttpHeaders.AUTHORIZATION, aluno))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat/" + diretor).header(HttpHeaders.AUTHORIZATION, aluno))
                .andExpect(status().isOk());

        // Fora da tabela de interlocutores: ele mesmo, o admin e um id que não existe.
        // O inexistente também é 403 — um 404 diria quais ids existem.
        for (long outro : new long[]{eu, admin, 9_999_999L}) {
            mvc.perform(get("/api/chat/" + outro).header(HttpHeaders.AUTHORIZATION, aluno))
                    .andExpect(status().isForbidden());
        }

        // Professor não lê a conversa de dois colegas de papel.
        mvc.perform(get("/api/chat/" + idDe("professor")).header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isForbidden());
    }

    // ── VULN-09: logout sem access token ─────────────────────────────────────

    @Test
    @DisplayName("logout sem access token revoga o refresh token pelo cookie e o apaga no navegador")
    void logoutSemAccessTokenRevogaOCookie() throws Exception {
        MockHttpServletResponse sessao = loginHttp("diretor", SENHA_PADRAO);
        Cookie cookie = cookieDeRefresh(sessao);

        // Sem Authorization: é o que acontece quando o access token já expirou.
        MockHttpServletResponse resposta = mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(resposta.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith("nexo_refresh=;") || h.contains("Max-Age=0"));

        // O ponto da correção: antes o servidor respondia 401 e o cookie seguia válido.
        mvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout com access token expirado ou inválido também revoga pelo cookie")
    void logoutComBearerInvalido() throws Exception {
        MockHttpServletResponse sessao = loginHttp("diretor", SENHA_PADRAO);
        Cookie cookie = cookieDeRefresh(sessao);

        mvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.vencido.qualquer")
                        .cookie(cookie))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout sem credencial alguma, ou com cookie desconhecido, é idempotente")
    void logoutAnonimoEIdempotente() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("nexo_refresh", "valor-que-nao-existe")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("logout anônimo não derruba a sessão de outro dispositivo")
    void logoutAnonimoAfetaSoOCookieApresentado() throws Exception {
        MockHttpServletResponse disp1 = loginHttp("diretor", SENHA_PADRAO);
        MockHttpServletResponse disp2 = loginHttp("diretor", SENHA_PADRAO);

        mvc.perform(post("/api/auth/logout").cookie(cookieDeRefresh(disp1)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").cookie(cookieDeRefresh(disp2)))
                .andExpect(status().isOk());
    }

    // ── Revogação por usuário (conta desativada) ─────────────────────────────

    @Test
    @DisplayName("desativar a conta derruba o access token já emitido, e reativar libera um novo")
    void desativarContaDerrubaOAccessToken() throws Exception {
        String admin = bearer("admin");
        JsonNode login = autenticar("aluno", SENHA_PADRAO);
        String tokenAluno = "Bearer " + login.get("token").asText();
        long alunoId = login.get("usuario").get("id").asLong();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenAluno))
                .andExpect(status().isOk());

        try {
            mvc.perform(patch("/api/admin/contas/" + alunoId + "/ativo")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")
                            .header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk());

            // Antes seguia aceito até o exp (15 min): "conta desativada" era só um rótulo.
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tokenAluno))
                    .andExpect(status().isUnauthorized());
        } finally {
            mvc.perform(patch("/api/admin/contas/" + alunoId + "/ativo")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}")
                    .header(HttpHeaders.AUTHORIZATION, admin));
        }

        // Reativada a conta, um login novo (emitido depois da revogação) vale normalmente —
        // sem espera: o token carrega o instante de emissão em milissegundos.
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isOk());
    }

    // ── VULN-02: revogação sobrevive a reinício ──────────────────────────────

    @Test
    @DisplayName("logout e revogação de usuário são relidos do banco numa instância nova")
    void revogacaoSobreviveAoReinicio() {
        revogados.revogar("jti-do-logout-f26");
        revogados.revogarUsuario(987_654L);
        assertThat(repositorioRevogados.findById("jti:jti-do-logout-f26")).isPresent();
        assertThat(repositorioRevogados.findById("uid:987654")).isPresent();

        // Instância nova = JVM reiniciada: memória vazia, só o banco resta.
        var depoisDoReinicio = new AccessTokensRevogados(15, repositorioRevogados,
                mock(ApplicationEventPublisher.class));
        assertThat(depoisDoReinicio.revogado("jti-do-logout-f26")).isFalse();
        depoisDoReinicio.carregarDoBanco();

        assertThat(depoisDoReinicio.revogado("jti-do-logout-f26")).isTrue();
        assertThat(depoisDoReinicio.revogado("outro-jti")).isFalse();
        assertThat(depoisDoReinicio.usuarioRevogado(987_654L, java.time.Instant.now().minusSeconds(60))).isTrue();
        assertThat(depoisDoReinicio.usuarioRevogado(987_654L, java.time.Instant.now().plusSeconds(60))).isFalse();
    }

    // ── VULN-07: avisos por destinatário ─────────────────────────────────────

    private long publicarAviso(String bearer, String titulo, String destino) throws Exception {
        String corpo = destino == null
                ? "{\"titulo\":\"" + titulo + "\",\"conteudo\":\"texto\"}"
                : "{\"titulo\":\"" + titulo + "\",\"conteudo\":\"texto\",\"destino\":\"" + destino + "\"}";
        return json.readTree(mvc.perform(post("/api/avisos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private Set<String> titulos(JsonNode avisos) {
        Set<String> t = new HashSet<>();
        avisos.forEach(a -> t.add(a.get("titulo").asText()));
        return t;
    }

    @Test
    @DisplayName("cada perfil lê só os avisos do próprio alcance; fora dele o id responde 404")
    void avisosPorDestinatario() throws Exception {
        String diretor = bearer("diretor");
        String professor = bearer("professor");
        String aluno = bearer("aluno");

        String[] turmas = {"9º Ano A", "9º Ano B", "1º Ano EM A", "2º Ano EM A"};
        List<Long> ids = new ArrayList<>();
        for (String turma : turmas) ids.add(publicarAviso(diretor, "F26 " + turma, turma));
        long geral = publicarAviso(diretor, "F26 geral", null);

        // O diretor audita tudo.
        Set<String> doDiretor = titulos(obter("/api/avisos", diretor));
        assertThat(doDiretor).contains("F26 geral", "F26 9º Ano A", "F26 9º Ano B",
                "F26 1º Ano EM A", "F26 2º Ano EM A");

        // O professor (História) leciona 9º A, 9º B e 2º EM A — e só.
        Set<String> doProfessor = titulos(obter("/api/avisos", professor));
        assertThat(doProfessor).contains("F26 geral", "F26 9º Ano A", "F26 9º Ano B", "F26 2º Ano EM A")
                .doesNotContain("F26 1º Ano EM A");

        // O aluno vê o geral e, no máximo, o aviso da própria turma — nunca o das outras.
        Set<String> doAluno = titulos(obter("/api/avisos", aluno));
        assertThat(doAluno).contains("F26 geral");
        long deTurma = doAluno.stream().filter(t -> t.startsWith("F26 ") && !t.equals("F26 geral")).count();
        assertThat(deTurma).isEqualTo(1);

        // Id sequencial de aviso alheio: 404, igual a um id que não existe.
        for (int i = 0; i < turmas.length; i++) {
            boolean visivel = doAluno.contains("F26 " + turmas[i]);
            mvc.perform(get("/api/avisos/" + ids.get(i)).header(HttpHeaders.AUTHORIZATION, aluno))
                    .andExpect(status().is(visivel ? 200 : 404));
        }
        mvc.perform(get("/api/avisos/" + geral).header(HttpHeaders.AUTHORIZATION, aluno))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("o destino do aviso precisa ser 'Todos' ou uma turma real — e do professor, uma que ele leciona")
    void destinoDoAvisoEValidado() throws Exception {
        String professor = bearer("professor");
        String diretor = bearer("diretor");

        mvc.perform(post("/api/avisos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"x\",\"conteudo\":\"y\",\"destino\":\"Apenas Professores\"}")
                        .header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isBadRequest());

        // 1º Ano EM A é de outro professor.
        mvc.perform(post("/api/avisos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"x\",\"conteudo\":\"y\",\"destino\":\"1º Ano EM A\"}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isForbidden());

        // A dele, e o geral, passam; vazio vira "Todos".
        publicarAviso(professor, "F26 do professor", "9º Ano A");
        mvc.perform(post("/api/avisos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"vazio\",\"conteudo\":\"y\",\"destino\":\"  \"}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.destino").value("Todos"));
    }

    @Test
    @DisplayName("texto acima da coluna é recusado com 400, e não estoura no banco como 500")
    void textosAcimaDoLimite() throws Exception {
        String longo = "a".repeat(4001);
        mvc.perform(post("/api/avisos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"t\",\"conteudo\":\"" + longo + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/avisos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + "t".repeat(256) + "\",\"conteudo\":\"c\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/questoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enunciado\":\"" + longo + "\",\"disciplina\":\"História\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isBadRequest());
    }

    // ── VULN-06: período das notas ───────────────────────────────────────────

    @Test
    @DisplayName("período de nota fora do formato ano-período é recusado")
    void periodoDaNotaEValidado() throws Exception {
        String professor = bearer("professor");
        long turmaId = -1;
        for (JsonNode t : obter("/api/turmas", bearer("diretor"))) {
            if ("9º Ano A".equals(t.get("nome").asText())) turmaId = t.get("id").asLong();
        }
        long alunoId = obter("/api/turmas/" + turmaId + "/frequencia", professor).get(0).get("alunoId").asLong();

        for (String invalido : new String[]{"hack", "2099-99", "2026-9", "2026-0", "../2026-1", ""}) {
            mvc.perform(patch("/api/alunos/" + alunoId + "/notas").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"disciplina\":\"História\",\"periodo\":\"" + invalido + "\",\"p1\":8.0}")
                            .header(HttpHeaders.AUTHORIZATION, professor))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/alunos/" + alunoId + "/notas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disciplina\":\"História\",\"periodo\":\"2026-2\",\"p1\":8.0}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isOk());
        // Sem período continua valendo o padrão.
        mvc.perform(patch("/api/alunos/" + alunoId + "/notas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disciplina\":\"História\",\"p1\":8.0}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isOk());
    }

    // ── VULN-10: banco de questões ───────────────────────────────────────────

    @Test
    @DisplayName("a listagem de questões tem teto por chamada e informa o total")
    void questoesTemTetoPorChamada() throws Exception {
        String professor = bearer("professor");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/questoes").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enunciado\":\"Pergunta F26 " + i + "\",\"disciplina\":\"História\"}")
                            .header(HttpHeaders.AUTHORIZATION, professor))
                    .andExpect(status().isCreated());
        }

        // Sem parâmetros continua uma lista simples (a tela atual não muda).
        assertThat(obter("/api/questoes", professor).isArray()).isTrue();

        MockHttpServletResponse limitada = mvc.perform(get("/api/questoes?size=2&page=0")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andReturn().getResponse();
        assertThat(json.readTree(limitada.getContentAsString()).size()).isEqualTo(2);
        assertThat(Long.parseLong(limitada.getHeader("X-Total-Count"))).isGreaterThanOrEqualTo(3);

        // Pedir mais que o teto não passa dele; valores absurdos não viram erro.
        mvc.perform(get("/api/questoes?size=1000000&page=-4").header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isOk());
    }

    // ── VULN-08: foto ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a foto é servida com CSP que impede qualquer conteúdo ativo")
    void fotoVemComCspRestritivo() throws Exception {
        byte[] png = new byte[72];
        byte[] assinatura = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(assinatura, 0, png, 0, assinatura.length);

        String url = json.readTree(mvc.perform(multipart("/api/usuarios/me/foto")
                        .file(new MockMultipartFile("arquivo", "x.png", "image/png", png))
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("foto").asText();

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    // ── VULN-15: Retry-After legível pelo cliente ────────────────────────────

    @Test
    @DisplayName("o CORS expõe Retry-After para o front de outra origem ler o tempo de espera")
    void retryAfterEhExposto() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("Retry-After")));
    }

    // ── Achado de QA: oráculo de senha atual ─────────────────────────────────

    @Test
    @DisplayName("errar a senha atual na troca de senha trava a conta após 5 tentativas")
    void trocaDeSenhaTemLimiteDeErros() throws Exception {
        // "admin": nenhum outro teste desta classe depende da senha ou da troca dele.
        String admin = bearer("admin");
        String corpoErrado = "{\"senhaAtual\":\"errada-errada\",\"novaSenha\":\"Nova-Senha-Forte-9!\"}";

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/usuarios/me/senha").contentType(MediaType.APPLICATION_JSON)
                            .content(corpoErrado).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isBadRequest());
        }
        // A sexta é barrada antes de comparar — nem a senha certa passa enquanto durar o bloqueio.
        mvc.perform(post("/api/usuarios/me/senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA_PADRAO + "\",\"novaSenha\":\"Nova-Senha-Forte-9!\"}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
