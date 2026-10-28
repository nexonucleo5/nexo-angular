package com.nexo;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * O alerta por webhook: um servidor local faz o papel do Slack/Discord. Cobre o que o torna
 * seguro de ligar — só ERROR, sem enxurrada e sem segredo.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-alerta;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true",
        "nexo.logs.alerta-intervalo-min=10"
})
class AlertaErroTest extends TesteApiBase {

    private static final Logger log = LoggerFactory.getLogger("teste.alerta");
    private static final List<String> recebidos = new CopyOnWriteArrayList<>();
    private static final HttpServer servidor = iniciar();

    private static HttpServer iniciar() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/hook", troca -> {
                recebidos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                troca.sendResponseHeaders(204, -1);
                troca.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void webhook(DynamicPropertyRegistry registry) {
        registry.add("nexo.logs.alerta-webhook-url",
                () -> "http://127.0.0.1:" + servidor.getAddress().getPort() + "/hook");
    }

    @AfterAll
    static void parar() {
        servidor.stop(0);
    }

    private List<String> contendo(String trecho) {
        return recebidos.stream().filter(b -> b.contains(trecho)).toList();
    }

    @Test
    @DisplayName("ERROR avisa; o mesmo erro repetido na janela não avisa de novo")
    void avisaUmaVezPorJanela() throws Exception {
        String marca = "alerta-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) log.error(marca);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contendo(marca)).hasSize(1));
        Thread.sleep(800); // dá tempo de um envio indevido aparecer
        assertThat(contendo(marca)).as("o laço de 5 erros vira 1 aviso").hasSize(1);
        assertThat(contendo(marca).get(0)).contains("[NEXO ERROR]").contains("\"text\"").contains("\"content\"");
    }

    @Test
    @DisplayName("WARN não avisa")
    void avisoNaoAlerta() throws Exception {
        String aviso = "so-aviso-" + UUID.randomUUID();
        String erro = "erro-depois-" + UUID.randomUUID();
        log.warn(aviso);
        log.error(erro);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contendo(erro)).hasSize(1));
        assertThat(contendo(aviso)).isEmpty();
    }

    @Test
    @DisplayName("o aviso sai sem segredo e sem stack trace")
    void semSegredoNemTrace() {
        String marca = "limpo-" + UUID.randomUUID();
        log.error("{} senha=Abc12345", marca, new IllegalStateException("detalhe-so-do-trace jdbc:postgresql://u:p4ss@h/db"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(contendo(marca)).hasSize(1));
        String corpo = contendo(marca).get(0);
        assertThat(corpo).doesNotContain("Abc12345").doesNotContain("detalhe-so-do-trace").doesNotContain("p4ss");
    }
}
