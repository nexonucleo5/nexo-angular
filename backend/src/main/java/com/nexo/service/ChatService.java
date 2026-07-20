package com.nexo.service;

import com.nexo.domain.ChatMensagem;
import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.ChatMensagemRepository;
import com.nexo.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Chat direto em tempo real entre usuários (professor ↔ diretor). Persiste o histórico. */
@Service
public class ChatService {

    public record ContatoDTO(Long id, String nome, String papel) {}

    public record ChatMensagemDTO(Long id, Long de, String deNome, Long para, String texto, Instant criadaEm) {
        static ChatMensagemDTO of(ChatMensagem m) {
            return new ChatMensagemDTO(m.getId(), m.getRemetenteId(), m.getRemetenteNome(),
                    m.getDestinatarioId(), m.getTexto(), m.getCriadaEm());
        }
    }

    private final ChatMensagemRepository mensagens;
    private final UsuarioRepository usuarios;

    public ChatService(ChatMensagemRepository mensagens, UsuarioRepository usuarios) {
        this.mensagens = mensagens;
        this.usuarios = usuarios;
    }

    /** Contatos disponíveis: professor fala com diretores; diretor fala com professores. */
    @Transactional(readOnly = true)
    public List<ContatoDTO> contatos(Long usuarioId) {
        Usuario eu = usuarios.findById(usuarioId).orElse(null);
        if (eu == null) return List.of();
        List<Role> alvo = eu.getRole() == Role.DIRETOR ? List.of(Role.PROFESSOR) : List.of(Role.DIRETOR);
        return usuarios.findByRoleInOrderByNome(alvo).stream()
                .filter(u -> !u.getId().equals(usuarioId))
                .map(u -> new ContatoDTO(u.getId(), u.getNome(), u.getRole().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMensagemDTO> historico(Long usuarioId, Long outroId) {
        return mensagens.conversa(usuarioId, outroId).stream().map(ChatMensagemDTO::of).toList();
    }

    @Transactional
    public ChatMensagemDTO salvar(Long remetenteId, String remetenteNome, Long destinatarioId, String texto) {
        ChatMensagem m = new ChatMensagem(remetenteId, remetenteNome, destinatarioId, texto.trim(), Instant.now());
        return ChatMensagemDTO.of(mensagens.save(m));
    }
}
