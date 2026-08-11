package com.nexo.service;

import com.nexo.domain.ChatMensagem;
import com.nexo.domain.Role;
import com.nexo.repository.ChatMensagemRepository;
import com.nexo.repository.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Chat direto em tempo real entre usuários de papéis diferentes. Persiste o histórico. */
@Service
public class ChatService {

    /** Teto de mensagens devolvidas por conversa — ver {@link #historico}. */
    private static final int LIMITE_HISTORICO = 200;

    public record ContatoDTO(Long id, String nome, String papel, String foto) {}

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
     *
     * <p>Tabela montada uma vez: as listas são imutáveis e não dependem de nada em
     * tempo de execução, então não faz sentido alocar uma nova a cada chamada — e
     * {@link #podeConversar} é chamado a cada mensagem que trafega no WebSocket.
     */
    private static final Map<Role, List<Role>> INTERLOCUTORES = new EnumMap<>(Map.of(
            Role.ALUNO,     List.of(Role.PROFESSOR, Role.DIRETOR),
            Role.PROFESSOR, List.of(Role.ALUNO, Role.DIRETOR),
            Role.DIRETOR,   List.of(Role.ALUNO, Role.PROFESSOR)));

    /** Contatos disponíveis para o usuário, conforme {@link #INTERLOCUTORES}. */
    @Transactional(readOnly = true)
    public List<ContatoDTO> contatos(Long usuarioId) {
        Role meuPapel = usuarios.findRoleById(usuarioId).orElse(null);
        if (meuPapel == null) return List.of();
        return usuarios.findByRoleInOrderByNome(INTERLOCUTORES.get(meuPapel)).stream()
                .filter(u -> !u.getId().equals(usuarioId))
                .map(u -> new ContatoDTO(u.getId(), u.getNome(), u.getRole().name(), u.getFoto()))
                .toList();
    }

    /**
     * O par pode trocar mensagens? Verificado no servidor a cada envio, já que o
     * destinatário chega pelo WebSocket e não dá para confiar apenas na lista do cliente.
     * Consulta só a coluna do papel — carregar as duas entidades aqui enchia o contexto
     * de persistência a cada mensagem trocada.
     */
    @Transactional(readOnly = true)
    public boolean podeConversar(Long remetenteId, Long destinatarioId) {
        if (remetenteId == null || destinatarioId == null || remetenteId.equals(destinatarioId)) return false;
        Role de = usuarios.findRoleById(remetenteId).orElse(null);
        Role para = usuarios.findRoleById(destinatarioId).orElse(null);
        if (de == null || para == null) return false;
        return INTERLOCUTORES.get(de).contains(para);
    }

    /**
     * Últimas mensagens da conversa, em ordem cronológica. O corte em
     * {@link #LIMITE_HISTORICO} dá teto ao consumo de memória do endpoint: a tela
     * mostra o fim da conversa, e sem limite o request piorava indefinidamente
     * conforme o par acumulava histórico.
     */
    @Transactional(readOnly = true)
    public List<ChatMensagemDTO> historico(Long usuarioId, Long outroId) {
        List<ChatMensagem> recentes =
                mensagens.conversaRecente(usuarioId, outroId, PageRequest.of(0, LIMITE_HISTORICO));

        // A query vem do mais novo para o mais antigo (é assim que se pega o fim da
        // lista no banco); a resposta precisa da ordem cronológica.
        List<ChatMensagemDTO> historico = new ArrayList<>(recentes.size());
        for (int i = recentes.size() - 1; i >= 0; i--) {
            historico.add(ChatMensagemDTO.of(recentes.get(i)));
        }
        return historico;
    }

    @Transactional
    public ChatMensagemDTO salvar(Long remetenteId, String remetenteNome, Long destinatarioId, String texto) {
        ChatMensagem m = new ChatMensagem(remetenteId, remetenteNome, destinatarioId, texto.trim(), Instant.now());
        return ChatMensagemDTO.of(mensagens.save(m));
    }
}
