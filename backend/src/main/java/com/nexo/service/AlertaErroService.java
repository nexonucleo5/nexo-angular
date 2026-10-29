package com.nexo.service;

import com.nexo.domain.LogAplicacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Avisa por webhook quando o sistema registra um ERROR — o que transforma o log de "algo que
 * se descobre abrindo a tela" em algo que chega a quem pode agir.
 *
 * <p>Compatível com Slack e Discord (o corpo leva {@code text} e {@code content}). Desligado
 * enquanto {@code nexo.logs.alerta-webhook-url} estiver vazio, que é o padrão.
 *
 * <ul>
 *   <li><b>Só ERROR.</b> WARN é frequente demais (403 e lentidão) e treinaria quem recebe a
 *       ignorar o canal.</li>
 *   <li><b>Sem enxurrada.</b> O mesmo erro (origem e mensagem) alerta na primeira vez e depois
 *       só a cada {@code alerta-intervalo-min}, com a contagem do que foi suprimido. Um laço
 *       de erro vira uma mensagem por janela, não mil.</li>
 *   <li><b>Sem segredo e sem stack trace.</b> O texto sai da mensagem já mascarada (ver
 *       {@link RedatorSegredos}); o trace fica na tela, atrás do login. Um webhook é um serviço
 *       de terceiros — nada que não pudesse estar num chat da equipe vai para lá.</li>
 *   <li><b>Falha em silêncio.</b> Webhook fora do ar não pode atrapalhar a gravação do log nem
 *       gerar log novo (que alertaria de novo). A chamada é assíncrona, com prazo curto.</li>
 * </ul>
 */
@Component
public class AlertaErroService {

    private record Janela(long inicioMs, long suprimidos) {}

    private final URI webhook;
    private final long intervaloMs;
    private final HttpClient http;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public AlertaErroService(@Value("${nexo.logs.alerta-webhook-url:}") String url,
                             @Value("${nexo.logs.alerta-intervalo-min:10}") int intervaloMin) {
        this.webhook = uriValida(url);
        this.intervaloMs = Math.max(intervaloMin, 0) * 60_000L;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean ativo() {
        return webhook != null;
    }

    /** Só aceita http(s) com host: qualquer outra coisa deixa o alerta desligado em vez de quebrar o boot. */
    private static URI uriValida(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.trim());
            boolean web = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            return web && uri.getHost() != null ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void notificar(LogAplicacao log) {
        if (webhook == null || log.getNivel() != LogAplicacao.Nivel.ERROR) return;

        String chave = log.getOrigem() + "|" + log.getMensagem();
        long agora = System.currentTimeMillis();
        if (janelas.size() > 500) janelas.clear();

        // Abre uma janela nova (e envia) se não há uma em curso; dentro de uma em curso só conta.
        // Janela recém-aberta tem suprimidos == 0, e é o que distingue "enviar" de "suprimir".
        long[] daJanelaAnterior = {0};
        Janela atual = janelas.compute(chave, (k, anterior) -> {
            if (anterior == null || agora - anterior.inicioMs() >= intervaloMs) {
                daJanelaAnterior[0] = anterior == null ? 0 : anterior.suprimidos();
                return new Janela(agora, 0);
            }
            return new Janela(anterior.inicioMs(), anterior.suprimidos() + 1);
        });
        if (atual.suprimidos() > 0) return;

        enviarTexto(texto(log, daJanelaAnterior[0]));
    }

    private static String texto(LogAplicacao l, long repeticoes) {
        StringBuilder t = new StringBuilder("[NEXO ERROR] ").append(l.getMensagem());
        t.append(" — ").append(l.getOrigem());
        if (l.getCaminho() != null) t.append(" — ").append(l.getMetodo()).append(' ').append(l.getCaminho());
        if (l.getRequisicaoId() != null) t.append(" — id ").append(l.getRequisicaoId());
        if (repeticoes > 0) t.append(" (+").append(repeticoes).append(" repetições desde o último aviso)");
        return t.length() > 1800 ? t.substring(0, 1800) : t.toString();
    }

    private void enviarTexto(String texto) {
        String json = "{\"text\":" + aspas(texto) + ",\"content\":" + aspas(texto) + "}";
        HttpRequest req = HttpRequest.newBuilder(webhook)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> null);
    }

    private static String aspas(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
