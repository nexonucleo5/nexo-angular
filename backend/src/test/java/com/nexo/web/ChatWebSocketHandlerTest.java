package com.nexo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.security.AccessTokensRevogados;
import com.nexo.security.WsTicketService;
import com.nexo.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O handler do chat é a única porta de entrada de mensagens em tempo real: aqui ficam
 * travados o teto de tamanho, o limite por usuário, a tolerância a quadros inválidos e o
 * fechamento do socket quando a sessão que o abriu é revogada.
 */
class ChatWebSocketHandlerTest {

    private WsTicketService tickets;
    private ChatService chat;
    private ChatWebSocketHandler handler;

    @BeforeEach
    void preparar() {
        tickets = mock(WsTicketService.class);
        chat = mock(ChatService.class);
        handler = new ChatWebSocketHandler(tickets, chat, new ObjectMapper());
        when(chat.podeConversar(anyLong(), anyLong())).thenReturn(true);
    }

    /** Sessão já autenticada pelo ticket, registrada no handler como numa conexão real. */
    private WebSocketSession conectar(long uid, String jti) throws Exception {
        WebSocketSession sessao = mock(WebSocketSession.class);
        Map<String, Object> atributos = new ConcurrentHashMap<>();
        when(sessao.getAttributes()).thenReturn(atributos);
        when(sessao.getUri()).thenReturn(URI.create("ws://localhost/ws/chat?ticket=t" + uid + jti));
        when(sessao.isOpen()).thenReturn(true);
        when(tickets.consumir("t" + uid + jti))
                .thenReturn(Optional.of(new WsTicketService.Titular(uid, "Fulano " + uid, jti)));
        handler.afterConnectionEstablished(sessao);
        return sessao;
    }

    private void enviar(WebSocketSession sessao, String payload) throws Exception {
        handler.handleTextMessage(sessao, new TextMessage(payload));
    }

    private static String mensagem(long para, String texto) {
        return "{\"para\":" + para + ",\"texto\":\"" + texto + "\"}";
    }

    @Test
    @DisplayName("mensagem dentro do limite é salva e distribuída")
    void mensagemNormalPassa() throws Exception {
        WebSocketSession sessao = conectar(1, "jti-1");

        enviar(sessao, mensagem(2, "oi"));

        verify(chat).salvar(1L, "Fulano 1", 2L, "oi");
    }

    @Test
    @DisplayName("texto acima de 2000 caracteres é descartado; exatamente 2000 passa")
    void tetoDeTamanho() throws Exception {
        WebSocketSession sessao = conectar(1, "jti-1");

        enviar(sessao, mensagem(2, "a".repeat(ChatService.TAMANHO_MAXIMO_MENSAGEM + 1)));
        verify(chat, never()).salvar(anyLong(), anyString(), anyLong(), anyString());

        enviar(sessao, mensagem(2, "a".repeat(ChatService.TAMANHO_MAXIMO_MENSAGEM)));
        verify(chat, times(1)).salvar(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("quadro que não é JSON não derruba a conexão com exceção")
    void jsonInvalidoEhIgnorado() throws Exception {
        WebSocketSession sessao = conectar(1, "jti-1");

        enviar(sessao, "isto não é json {{{");
        enviar(sessao, "null");

        verify(chat, never()).salvar(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("par não permitido não grava nada")
    void parNaoPermitido() throws Exception {
        WebSocketSession sessao = conectar(1, "jti-1");
        when(chat.podeConversar(1L, 3L)).thenReturn(false);

        enviar(sessao, mensagem(3, "oi"));

        verify(chat, never()).salvar(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("depois de 60 mensagens na janela o resto é descartado, só para esse usuário")
    void limitePorUsuario() throws Exception {
        WebSocketSession inundador = conectar(1, "jti-1");
        WebSocketSession comportado = conectar(2, "jti-2");

        for (int i = 0; i < 80; i++) enviar(inundador, mensagem(9, "spam " + i));
        verify(chat, times(60)).salvar(anyLong(), anyString(), anyLong(), anyString());

        // O limite é por usuário: o outro segue enviando normalmente.
        enviar(comportado, mensagem(9, "oi"));
        verify(chat).salvar(2L, "Fulano 2", 9L, "oi");
    }

    @Test
    @DisplayName("logout fecha só os sockets abertos pela sessão revogada")
    void logoutFechaOSocketDaSessao() throws Exception {
        WebSocketSession doAparelho = conectar(1, "jti-do-logout");
        WebSocketSession doOutroAparelho = conectar(1, "jti-outro");

        handler.aoRevogarSessao(new AccessTokensRevogados.SessaoRevogada("jti-do-logout"));

        verify(doAparelho).close(CloseStatus.POLICY_VIOLATION);
        verify(doOutroAparelho, never()).close(any());
    }

    @Test
    @DisplayName("conta derrubada fecha todos os sockets do usuário e nenhum de outro")
    void usuarioRevogadoFechaTodosOsSockets() throws Exception {
        WebSocketSession aba1 = conectar(1, "jti-a");
        WebSocketSession aba2 = conectar(1, "jti-b");
        WebSocketSession deOutro = conectar(2, "jti-c");

        handler.aoRevogarUsuario(new AccessTokensRevogados.UsuarioRevogado(1L));

        verify(aba1).close(CloseStatus.POLICY_VIOLATION);
        verify(aba2).close(CloseStatus.POLICY_VIOLATION);
        verify(deOutro, never()).close(any());
    }

    @Test
    @DisplayName("ticket inválido fecha a conexão antes de aceitar mensagens")
    void ticketInvalido() throws Exception {
        WebSocketSession sessao = mock(WebSocketSession.class);
        when(sessao.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(sessao.getUri()).thenReturn(URI.create("ws://localhost/ws/chat?ticket=forjado"));
        when(tickets.consumir("forjado")).thenReturn(Optional.empty());

        handler.afterConnectionEstablished(sessao);
        enviar(sessao, mensagem(2, "oi"));

        verify(sessao).close(CloseStatus.POLICY_VIOLATION);
        verify(chat, never()).salvar(anyLong(), anyString(), anyLong(), anyString());
    }
}
