package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.security.WsTicketService;
import com.nexo.service.ChatService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Histórico e contatos do chat direto. O envio é via WebSocket. */
@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasAnyRole('ALUNO','PROFESSOR','DIRETOR')")
public class ChatController {

    private final ChatService chat;
    private final WsTicketService wsTickets;

    public ChatController(ChatService chat, WsTicketService wsTickets) {
        this.chat = chat;
        this.wsTickets = wsTickets;
    }

    @GetMapping("/contatos")
    public List<ChatService.ContatoDTO> contatos(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return chat.contatos(principal.id());
    }

    @GetMapping("/{outroId}")
    public List<ChatService.ChatMensagemDTO> historico(@AuthenticationPrincipal UsuarioAutenticado principal,
                                                       @PathVariable Long outroId) {
        return chat.historico(principal.id(), outroId);
    }

    /** Ticket de uso único (30s) para o handshake do WebSocket — ver WsTicketService. */
    @GetMapping("/ws-ticket")
    public Map<String, String> wsTicket(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return Map.of("ticket", wsTickets.emitir(principal.id(), principal.nome()));
    }
}
