package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.domain.LogAplicacao;
import com.nexo.repository.LogAplicacaoRepository;
import com.nexo.service.LogAplicacaoAppender;
import com.nexo.service.LogsRetencaoJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O log da aplicação chega à tabela e só o administrador o lê. A gravação é assíncrona
 * (para nunca atrasar quem loga), então as asserções esperam o registro aparecer.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-logs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true",
        "nexo.logs.nivel-minimo=WARN",
        "nexo.logs.max-por-minuto=20"
})
class LogsSistemaTest extends TesteApiBase {

    private static final Logger log = LoggerFactory.getLogger("teste.logs");

    @Autowired LogAplicacaoRepository logs;
    @Autowired LogAplicacaoAppender appender;
    @Autowired LogsRetencaoJob retencao;

    private List<LogAplicacao> gravados(String trecho) {
        return logs.findAll().stream().filter(l -> l.getMensagem().contains(trecho)).toList();
    }

    private void esperarGravar(String trecho) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(gravados(trecho)).isNotEmpty());
    }

    @Test
    @DisplayName("só o administrador lê os logs e o resumo — nem o diretor")
    void soOAdministradorLe() throws Exception {
        for (String perfil : new String[]{"diretor", "professor", "aluno"}) {
            for (String rota : new String[]{"/api/auditoria/sistema/logs", "/api/auditoria/sistema/resumo"}) {
                mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                        .andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/api/auditoria/sistema/logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria/sistema/logs").header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ERROR com exceção é gravado com origem, mensagem e stack trace")
    void erroComExcecao() {
        String marca = "falha-" + UUID.randomUUID();
        log.error("Quebrou {}", marca, new IllegalStateException("causa-raiz"));

        esperarGravar(marca);
        LogAplicacao gravado = gravados(marca).get(0);
        assertThat(gravado.getNivel()).isEqualTo(LogAplicacao.Nivel.ERROR);
        assertThat(gravado.getOrigem()).isEqualTo("teste.logs");
        assertThat(gravado.getExcecao()).contains("IllegalStateException").contains("causa-raiz");
    }

    @Test
    @DisplayName("abaixo do nível mínimo não grava")
    void ignoraAbaixoDoMinimo() {
        String info = "so-info-" + UUID.randomUUID();
        String aviso = "aviso-" + UUID.randomUUID();
        log.info(info);
        log.warn(aviso);

        // A gravação é uma fila de um consumidor só: quando o aviso (posterior) está lá,
        // o info (anterior) já teria sido gravado se fosse para ser.
        esperarGravar(aviso);
        assertThat(gravados(info)).isEmpty();
        assertThat(gravados(aviso).get(0).getNivel()).isEqualTo(LogAplicacao.Nivel.WARN);
    }

    @Test
    @DisplayName("403 vira WARN com rota, usuário e o mesmo id que a resposta devolveu")
    void acessoNegadoViraAviso() throws Exception {
        var resposta = mvc.perform(get("/api/auditoria/eventos").header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isForbidden())
                .andReturn().getResponse();
        String id = resposta.getHeader("X-Request-Id");
        assertThat(id).as("toda resposta de /api traz X-Request-Id").isNotBlank();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(logs.findAll().stream().filter(l -> id.equals(l.getRequisicaoId())).toList()).isNotEmpty());
        LogAplicacao gravado = logs.findAll().stream().filter(l -> id.equals(l.getRequisicaoId())).findFirst().orElseThrow();
        assertThat(gravado.getNivel()).isEqualTo(LogAplicacao.Nivel.WARN);
        assertThat(gravado.getMensagem()).contains("403");
        assertThat(gravado.getMetodo()).isEqualTo("GET");
        assertThat(gravado.getCaminho()).isEqualTo("/api/auditoria/eventos");
        assertThat(gravado.getUsuario()).isEqualTo("aluno");
    }

    @Test
    @DisplayName("a mesma mensagem em loop é limitada por minuto, e o excedente é contado")
    void cotaPorMinuto() {
        String marca = "loop-" + UUID.randomUUID();
        long antes = appender.descartados();

        for (int i = 0; i < 50; i++) log.warn(marca);

        assertThat(appender.descartados() - antes).isEqualTo(30);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(gravados(marca)).hasSize(20));
    }

    @Test
    @DisplayName("o resumo conta erros e avisos das últimas 24h e confirma o banco")
    void resumo() throws Exception {
        String marca = "resumo-" + UUID.randomUUID();
        log.error(marca);
        esperarGravar(marca);

        JsonNode resumo = json.readTree(mvc.perform(get("/api/auditoria/sistema/resumo")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(resumo.get("erros24h").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(resumo.get("ultimoErro").isNull()).isFalse();
        assertThat(resumo.get("bancoOk").asBoolean()).isTrue();
        assertThat(resumo.get("heapMaxMb").asLong()).isPositive();
        assertThat(resumo.get("uptimeSegundos").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("lista filtra por nível e por trecho, e rejeita nível inexistente")
    void listaComFiltros() throws Exception {
        String marca = "filtro-" + UUID.randomUUID();
        log.error(marca);
        esperarGravar(marca);
        String admin = bearer("admin");

        JsonNode porBusca = json.readTree(mvc.perform(get("/api/auditoria/sistema/logs?nivel=ERROR&busca=" + marca.toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(porBusca.get("content")).hasSize(1);

        JsonNode outroNivel = json.readTree(mvc.perform(get("/api/auditoria/sistema/logs?nivel=INFO&busca=" + marca)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(outroNivel.get("content")).isEmpty();

        mvc.perform(get("/api/auditoria/sistema/logs?nivel=QUALQUER").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("segredos são mascarados ao gravar: senha na mensagem, credencial de banco no stack trace")
    void mascaraAoGravar() {
        String marca = "mascara-" + UUID.randomUUID();
        log.error("{} senha=Abc12345 token: xyz987abcdef", marca,
                new RuntimeException("falha em jdbc:postgresql://nexo:SenhaDoBanco77@db.exemplo.com/nexo"));

        esperarGravar(marca);
        LogAplicacao gravado = gravados(marca).get(0);
        assertThat(gravado.getMensagem()).doesNotContain("Abc12345").doesNotContain("xyz987abcdef");
        assertThat(gravado.getExcecao()).doesNotContain("SenhaDoBanco77").contains("[REDACTED]");
    }

    @Test
    @DisplayName("registro antigo, gravado cru, sai mascarado da API (e a tabela não é reescrita)")
    void mascaraAoLer() throws Exception {
        String marca = "cru-" + UUID.randomUUID();
        // Como a linha do print: gravada direto, sem passar pelo appender.
        LogAplicacao cru = novo(marca + " Using generated security password: 75b0e0cf-559f-42a0-882e-3f6d85bcbd1f",
                Instant.now());
        logs.save(cru);

        JsonNode pagina = json.readTree(mvc.perform(get("/api/auditoria/sistema/logs?busca=" + marca)
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String mensagem = pagina.get("content").get(0).get("mensagem").asText();
        assertThat(mensagem).doesNotContain("75b0e0cf").contains("[REDACTED]");
    }

    @Test
    @DisplayName("o Spring não gera mais a senha aleatória que ia parar no log")
    void semSenhaGerada(@Autowired org.springframework.context.ApplicationContext ctx) {
        assertThat(ctx.getBeanNamesForType(org.springframework.security.core.userdetails.UserDetailsService.class))
                .isEmpty();
    }

    @Test
    @DisplayName("ler os logs deixa rastro na auditoria, uma vez por sessão de consulta e sem copiar o que foi buscado")
    void leituraFicaNaAuditoria() throws Exception {
        String admin = bearer("admin");
        // Digitar na busca, trocar filtro e paginar são dezenas de requisições: viram UM evento.
        for (String consulta : new String[]{"?busca=segredo-buscado", "?nivel=ERROR", "?page=1", "/resumo"}) {
            String rota = consulta.equals("/resumo") ? "/api/auditoria/sistema/resumo"
                    : "/api/auditoria/sistema/logs" + consulta;
            mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        }

        var eventos = consultas("Logs do sistema consultados");
        assertThat(eventos).as("várias requisições na janela, um só evento").hasSize(1);
        assertThat(eventos.get(0).getTipo()).isEqualTo(com.nexo.domain.EventoAuditoria.Tipo.ACESSO);
        assertThat(eventos.get(0).getUsuarioNome()).isNotBlank();
        assertThat(eventos.get(0).getDetalhe()).as("o termo buscado não é copiado").isNull();
    }

    @Test
    @DisplayName("o resumo agrupa os problemas que mais se repetem e conta falhas de acesso")
    void resumoComPrincipaisProblemas() throws Exception {
        String marca = "repetido-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) log.warn(marca);
        loginHttp("nao-existe-resumo", "errada");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(gravados(marca)).hasSize(3));

        JsonNode resumo = json.readTree(mvc.perform(get("/api/auditoria/sistema/resumo")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode problema = null;
        for (JsonNode p : resumo.get("principaisProblemas")) {
            if (p.get("mensagem").asText().equals(marca)) problema = p;
        }
        assertThat(problema).as("o problema repetido aparece agrupado").isNotNull();
        assertThat(problema.get("total").asLong()).isEqualTo(3);
        assertThat(resumo.get("falhasDeAcesso24h").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(resumo.get("alertaAtivo").asBoolean()).as("sem webhook configurado").isFalse();
    }

    @Test
    @DisplayName("o resumo traz 24 horas completas para o gráfico, com a ocorrência de agora na última")
    void resumoPorHora() throws Exception {
        String marca = "grafico-" + UUID.randomUUID();
        log.error(marca);
        esperarGravar(marca);

        JsonNode resumo = json.readTree(mvc.perform(get("/api/auditoria/sistema/resumo")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode horas = resumo.get("porHora");
        assertThat(horas).as("sempre 24 baldes, mesmo os sem ocorrência").hasSize(24);
        for (int i = 1; i < 24; i++) {
            assertThat(Instant.parse(horas.get(i).get("hora").asText()))
                    .isEqualTo(Instant.parse(horas.get(i - 1).get("hora").asText()).plus(1, ChronoUnit.HOURS));
        }
        assertThat(horas.get(23).get("erros").asLong()).as("o erro de agora cai na hora corrente").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a sonda de saúde é pública e não revela nada além de UP")
    void sondaPublica() throws Exception {
        String corpo = mvc.perform(get("/api/saude"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(corpo).fieldNames()).toIterable().containsExactly("status");
        assertThat(json.readTree(corpo).get("status").asText()).isEqualTo("UP");
    }

    @Autowired com.nexo.repository.EventoAuditoriaRepository auditoriaRepo;

    private List<com.nexo.domain.EventoAuditoria> consultas(String acao) {
        return auditoriaRepo.findAll().stream()
                .filter(e -> acao.equals(e.getAcao()))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .toList();
    }

    @Test
    @DisplayName("a retenção apaga só o que passou do prazo")
    void retencao() {
        LogAplicacao velho = novo("velho-" + UUID.randomUUID(), Instant.now().minus(60, ChronoUnit.DAYS));
        LogAplicacao recente = novo("recente-" + UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS));
        logs.saveAll(List.of(velho, recente));

        retencao.limpar(); // prazo padrão: 30 dias

        assertThat(logs.findById(velho.getId())).isEmpty();
        assertThat(logs.findById(recente.getId())).isPresent();
    }

    private static LogAplicacao novo(String mensagem, Instant quando) {
        LogAplicacao l = new LogAplicacao();
        l.setNivel(LogAplicacao.Nivel.WARN);
        l.setOrigem("teste");
        l.setMensagem(mensagem);
        l.setCriadoEm(quando);
        return l;
    }
}
