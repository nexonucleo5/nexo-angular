package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.ChatService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Histórico e contatos do chat direto (professor ↔ diretor). O envio é via WebSocket. */
@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
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
}
