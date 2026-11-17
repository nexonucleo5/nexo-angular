package com.nexo.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.security.AccessTokensRevogados;
import com.nexo.security.JanelaDeTentativas;
import com.nexo.security.WsTicketService;
import com.nexo.service.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat em tempo real via WebSocket nativo. O handshake é autenticado por um
 * ticket de uso único (?ticket=...) obtido via GET /api/chat/ws-ticket, já que
 * o navegador não envia header Authorization no WebSocket e colocar o access
 * token completo na query string o exporia em logs de proxy/acesso. Cada
 * mensagem é persistida e entregue ao vivo ao destinatário (se conectado) e
 * ecoada ao remetente.
 *
 * <p>A conexão é longa e o handshake só autentica uma vez, então o que muda depois dele
 * precisa chegar aqui: quando o logout ou a derrubada da conta revoga a sessão que abriu o
 * socket, ele é fechado na hora ({@link #aoRevogarSessao}, {@link #aoRevogarUsuario}).
 * Sem isso o socket seguia entregando e aceitando mensagens depois de a pessoa ter saído.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WsTicketService tickets;
    private final ChatService chat;
    private final ObjectMapper mapper;

    /**
     * Teto de mensagens por usuário. A JanelaDeTentativas conta "falhas", aqui cada mensagem
     * enviada: ao atingir o máximo dentro da janela, o resto é descartado até ela vencer.
     * Um humano digitando não chega perto; um script em laço, sim. É em memória — mesmo
     * limite conhecido do resto dos limitadores (ver JanelaDeTentativas).
     */
    private static final int MAX_MENSAGENS_POR_JANELA = 60;
    private static final Duration JANELA_MENSAGENS = Duration.ofMinutes(1);
    private final JanelaDeTentativas porUsuario =
            new JanelaDeTentativas(MAX_MENSAGENS_POR_JANELA, JANELA_MENSAGENS);

    /** Sessões ativas por usuário (um usuário pode ter várias abas). */
    private final Map<Long, Set<WebSocketSession>> sessoes = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(WsTicketService tickets, ChatService chat, ObjectMapper mapper) {
        this.tickets = tickets;
        this.chat = chat;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Optional<WsTicketService.Titular> titular = autenticar(session);
        if (titular.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        Long uid = titular.get().uid();
        session.getAttributes().put("uid", uid);
        session.getAttributes().put("nome", titular.get().nome());
        // O mapa de atributos não aceita null; o jti sempre existe para quem passou pelo
        // JwtAuthFilter, mas o handler não precisa depender disso para não quebrar.
        if (titular.get().jti() != null) session.getAttributes().put("jti", titular.get().jti());
        sessoes.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long de = (Long) session.getAttributes().get("uid");
        String deNome = (String) session.getAttributes().get("nome");
        if (de == null) return;

        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (JsonProcessingException e) {
            return; // quadro que não é JSON: descarta, como os demais inválidos
        }
        if (node == null) return;
        Long para = node.hasNonNull("para") ? node.get("para").asLong() : null;
        String texto = node.hasNonNull("texto") ? node.get("texto").asText() : null;
        if (para == null || texto == null || texto.isBlank()) return;

        // Sem teto o texto ia direto para o banco: acima da coluna estourava e derrubava a
        // conexão; abaixo dela, mensagens enormes eram persistidas e redistribuídas.
        if (texto.length() > ChatService.TAMANHO_MAXIMO_MENSAGEM) return;

        // Limite por usuário: um laço de envio não deve gravar e redistribuir sem freio.
        String chaveLimite = String.valueOf(de);
        if (porUsuario.segundosDeBloqueio(chaveLimite) > 0) return;
        porUsuario.registrarFalha(chaveLimite);

        // O destinatário vem do cliente: confere no servidor se o par é permitido
        // (impede, por exemplo, aluno → aluno com um id forjado).
        if (!chat.podeConversar(de, para)) return;

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

    /** Logout de uma sessão: fecha os sockets abertos com o ticket pedido por aquele token. */
    @EventListener
    public void aoRevogarSessao(AccessTokensRevogados.SessaoRevogada evento) {
        for (Set<WebSocketSession> doUsuario : sessoes.values()) {
            for (WebSocketSession s : doUsuario) {
                if (evento.jti() != null && evento.jti().equals(s.getAttributes().get("jti"))) {
                    fechar(s);
                }
            }
        }
    }

    /** Conta desativada, senha redefinida ou sessão roubada: nenhum socket do usuário sobrevive. */
    @EventListener
    public void aoRevogarUsuario(AccessTokensRevogados.UsuarioRevogado evento) {
        Set<WebSocketSession> doUsuario = sessoes.get(evento.usuarioId());
        if (doUsuario == null) return;
        for (WebSocketSession s : doUsuario) {
            fechar(s);
        }
    }

    private static void fechar(WebSocketSession s) {
        try {
            s.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
            // já estava caindo; o afterConnectionClosed faz a limpeza
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

    private Optional<WsTicketService.Titular> autenticar(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return Optional.empty();
        for (String par : uri.getQuery().split("&")) {
            int i = par.indexOf('=');
            if (i > 0 && "ticket".equals(par.substring(0, i))) {
                return tickets.consumir(par.substring(i + 1));
            }
        }
        return Optional.empty();
    }
}
