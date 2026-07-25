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

/** Chat direto em tempo real entre usuários de papéis diferentes. Persiste o histórico. */
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

    /**
     * Papéis com quem cada perfil pode conversar. A conversa é sempre entre papéis
     * diferentes — aluno↔aluno, professor↔professor e diretor↔diretor não são permitidos.
     */
    private static List<Role> interlocutores(Role role) {
        return switch (role) {
            case ALUNO     -> List.of(Role.PROFESSOR, Role.DIRETOR);
            case PROFESSOR -> List.of(Role.ALUNO, Role.DIRETOR);
            case DIRETOR   -> List.of(Role.ALUNO, Role.PROFESSOR);
        };
    }

    /** Contatos disponíveis para o usuário, conforme {@link #interlocutores(Role)}. */
    @Transactional(readOnly = true)
    public List<ContatoDTO> contatos(Long usuarioId) {
        Usuario eu = usuarios.findById(usuarioId).orElse(null);
        if (eu == null) return List.of();
        return usuarios.findByRoleInOrderByNome(interlocutores(eu.getRole())).stream()
                .filter(u -> !u.getId().equals(usuarioId))
                .map(u -> new ContatoDTO(u.getId(), u.getNome(), u.getRole().name()))
                .toList();
    }

    /**
     * O par pode trocar mensagens? Verificado no servidor a cada envio, já que o
     * destinatário chega pelo WebSocket e não dá para confiar apenas na lista do cliente.
     */
    @Transactional(readOnly = true)
    public boolean podeConversar(Long remetenteId, Long destinatarioId) {
        if (remetenteId == null || destinatarioId == null || remetenteId.equals(destinatarioId)) return false;
        Usuario de = usuarios.findById(remetenteId).orElse(null);
        Usuario para = usuarios.findById(destinatarioId).orElse(null);
        if (de == null || para == null) return false;
        return interlocutores(de.getRole()).contains(para.getRole());
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
