package com.nexo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexo.domain.ChatMensagem;
import com.nexo.security.WsTicketService;
import com.nexo.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O texto da mensagem vem do cliente pelo WebSocket e vai para uma coluna com
 * tamanho fixo. Sem a conferência no handler, um texto acima do limite estourava
 * o insert já dentro do {@code handleTextMessage}: a exceção subia, o contêiner
 * do WebSocket encerrava a sessão, e quem estava conversando caía — no momento
 * escolhido por quem enviou. O {@code maxlength} do campo no Angular não conta
 * como defesa, porque o socket aceita qualquer cliente.
 */
class ChatWebSocketHandlerTest {

    private static final long REMETENTE = 1L;
    private static final long DESTINATARIO = 2L;
    private static final int LIMITE = ChatMensagem.TAMANHO_MAXIMO_TEXTO;

    private final WsTicketService tickets = mock(WsTicketService.class);
    private final ChatService chat = mock(ChatService.class);
    /** Com o módulo de datas, como o mapper que o Spring injeta no handler em produção. */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ChatWebSocketHandler handler = new ChatWebSocketHandler(tickets, chat, mapper);

    private WebSocketSession sessao;

    @BeforeEach
    void preparar() {
        sessao = mock(WebSocketSession.class);
        // O handler lê o remetente dos atributos que o handshake gravou na sessão.
        Map<String, Object> atributos = new HashMap<>();
        atributos.put("uid", REMETENTE);
        atributos.put("nome", "Prof. Roberto Alves");
        when(sessao.getAttributes()).thenReturn(atributos);

        when(chat.podeConversar(REMETENTE, DESTINATARIO)).thenReturn(true);
        when(chat.salvar(anyLong(), anyString(), anyLong(), anyString()))
                .thenAnswer(chamada -> new ChatService.ChatMensagemDTO(
                        10L, chamada.getArgument(0), chamada.getArgument(1),
                        chamada.getArgument(2), chamada.getArgument(3), Instant.now()));
    }

    private void enviar(String texto) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("para", DESTINATARIO, "texto", texto));
        handler.handleTextMessage(sessao, new TextMessage(payload));
    }

    @Test
    void persisteMensagemNoLimite() throws Exception {
        enviar("a".repeat(LIMITE));

        verify(chat).salvar(eq(REMETENTE), anyString(), eq(DESTINATARIO), anyString());
    }

    @Test
    void descartaMensagemAcimaDoLimiteSemDerrubarASessao() throws Exception {
        enviar("a".repeat(LIMITE + 1));

        // Não chega ao banco: é aí que a exceção nascia.
        verify(chat, never()).salvar(anyLong(), anyString(), anyLong(), anyString());
        // E a sessão segue de pé — o método retorna em vez de deixar algo subir.
        verify(sessao, never()).close();
    }

    /**
     * O texto é aparado antes de ser medido, e não depois. Do contrário uma mensagem
     * de tamanho válido com espaços em volta seria recusada por causa dos espaços,
     * que o {@code trim} descartaria em seguida.
     */
    @Test
    void aparaOsEspacosAntesDeMedir() throws Exception {
        enviar("   " + "a".repeat(LIMITE) + "   ");

        verify(chat).salvar(eq(REMETENTE), anyString(), eq(DESTINATARIO),
                argThat(texto -> texto.length() == LIMITE));
    }

    @Test
    void ignoraMensagemSoComEspacos() throws Exception {
        enviar("      ");

        verify(chat, never()).salvar(anyLong(), anyString(), anyLong(), anyString());
    }
}
