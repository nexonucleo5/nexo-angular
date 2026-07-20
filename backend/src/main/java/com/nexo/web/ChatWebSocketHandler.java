package com.nexo.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.security.JwtService;
import com.nexo.service.ChatService;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat em tempo real via WebSocket nativo. O handshake é autenticado por um
 * token JWT passado na query (?token=...), já que o navegador não envia header
 * Authorization no WebSocket. Cada mensagem é persistida e entregue ao vivo ao
 * destinatário (se conectado) e ecoada ao remetente.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwt;
    private final ChatService chat;
    private final ObjectMapper mapper;

    /** Sessões ativas por usuário (um usuário pode ter várias abas). */
    private final Map<Long, Set<WebSocketSession>> sessoes = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(JwtService jwt, ChatService chat, ObjectMapper mapper) {
        this.jwt = jwt;
        this.chat = chat;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Optional<Claims> claims = autenticar(session);
        if (claims.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        Long uid = ((Number) claims.get().get("uid")).longValue();
        session.getAttributes().put("uid", uid);
        session.getAttributes().put("nome", String.valueOf(claims.get().get("nome")));
        sessoes.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long de = (Long) session.getAttributes().get("uid");
        String deNome = (String) session.getAttributes().get("nome");
        if (de == null) return;

        JsonNode node = mapper.readTree(message.getPayload());
        Long para = node.hasNonNull("para") ? node.get("para").asLong() : null;
        String texto = node.hasNonNull("texto") ? node.get("texto").asText() : null;
        if (para == null || texto == null || texto.isBlank()) return;

        // Persiste e distribui
        ChatService.ChatMensagemDTO salva = chat.salvar(de, deNome, para, texto);
        String payload = mapper.writeValueAsString(salva);
        enviarPara(para, payload);
        enviarPara(de, payload); // eco para as outras abas do remetente
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = (Long) session.getAttributes().get("uid");
        if (uid == null) return;
        Set<WebSocketSession> set = sessoes.get(uid);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) sessoes.remove(uid);
        }
    }

    private void enviarPara(Long usuarioId, String payload) {
        Set<WebSocketSession> set = sessoes.get(usuarioId);
        if (set == null) return;
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(payload));
            } catch (Exception ignored) {
                // sessão instável: ignora, será limpa no close
            }
        }
    }

    private Optional<Claims> autenticar(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return Optional.empty();
        for (String par : uri.getQuery().split("&")) {
            int i = par.indexOf('=');
            if (i > 0 && "token".equals(par.substring(0, i))) {
                return jwt.validar(par.substring(i + 1));
            }
        }
        return Optional.empty();
    }
}
