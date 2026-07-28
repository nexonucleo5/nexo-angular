package com.nexo.security;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tickets de uso único e curta duração para autenticar o handshake do WebSocket.
 * O navegador não envia o header Authorization em WebSocket, então em vez de
 * colocar o access token completo na query string — onde pode acabar em logs de
 * proxy ou de acesso — o cliente troca o token por um ticket descartável só para
 * abrir a conexão.
 */
@Service
public class WsTicketService {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Titular(Long uid, String nome) {}

    private record Ticket(Titular titular, Instant expiraEm) {}

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String emitir(Long uid, String nome) {
        limparExpirados();
        String valor = gerarValor();
        tickets.put(valor, new Ticket(new Titular(uid, nome), Instant.now().plus(TTL)));
        return valor;
    }

    /** Consome o ticket — uso único, removido do mapa mesmo se já tiver expirado. */
    public Optional<Titular> consumir(String valor) {
        Ticket t = tickets.remove(valor);
        if (t == null || t.expiraEm().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(t.titular());
    }

    private void limparExpirados() {
        Instant agora = Instant.now();
        tickets.values().removeIf(t -> t.expiraEm().isBefore(agora));
    }

    private static String gerarValor() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
