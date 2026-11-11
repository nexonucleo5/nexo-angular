package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.ChatAnexo;
import com.nexo.domain.ChatMensagem;
import com.nexo.domain.ChatMensagemOculta;
import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.ChatAnexoRepository;
import com.nexo.repository.ChatMensagemOcultaRepository;
import com.nexo.repository.ChatMensagemRepository;
import com.nexo.repository.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Chat direto em tempo real entre usuários de papéis diferentes. Persiste o histórico. */
@Service
public class ChatService {

    /** Teto de mensagens devolvidas por conversa — ver {@link #historico}. */
    private static final int LIMITE_HISTORICO = 200;

    /** Anexos aceitos por mensagem (arquivos e/ou áudio gravado). */
    public static final int MAX_ANEXOS = 3;
    /** Tamanho máximo de cada anexo. O teto do multipart no application.yml acompanha este valor. */
    public static final long TAMANHO_MAXIMO_ANEXO = 5L * 1024 * 1024; // 5 MB
    private static final int LIMITE_TEXTO = 2000;

    public record ContatoDTO(Long id, String nome, String papel, String foto) {}

    public record AnexoDTO(String id, String nome, String tipo, long tamanho) {
        static AnexoDTO of(ChatAnexoRepository.Metadados a) {
            return new AnexoDTO(a.getId(), a.getNome(), a.getTipoConteudo(), a.getTamanho());
        }

        static AnexoDTO of(ChatAnexo a) {
            return new AnexoDTO(a.getId(), a.getNome(), a.getTipoConteudo(), a.getTamanho());
        }
    }

    public record ChatMensagemDTO(Long id, Long de, String deNome, Long para, String texto, Instant criadaEm,
                                  List<AnexoDTO> anexos) {
        static ChatMensagemDTO of(ChatMensagem m, List<AnexoDTO> anexos) {
            return new ChatMensagemDTO(m.getId(), m.getRemetenteId(), m.getRemetenteNome(),
                    m.getDestinatarioId(), m.getTexto(), m.getCriadaEm(), anexos);
        }
    }

    /** Evento enviado pelo WebSocket aos dois lados quando uma mensagem é apagada. */
    public record MensagemApagadaDTO(String tipo, Long id, Long de, Long para) {
        MensagemApagadaDTO(Long id, Long de, Long para) {
            this("APAGADA", id, de, para);
        }
    }

    /**
     * Evento enviado pelo WebSocket só às abas de quem apagou "para mim" — o outro
     * participante não fica sabendo.
     */
    public record MensagemOcultadaDTO(String tipo, Long id, Long de, Long para) {
        MensagemOcultadaDTO(Long id, Long de, Long para) {
            this("OCULTADA", id, de, para);
        }
    }

    /** Bytes de um anexo prontos para o download. */
    public record ConteudoAnexo(String nome, String tipo, byte[] dados) {}

    private final ChatMensagemRepository mensagens;
    private final ChatAnexoRepository anexos;
    private final ChatMensagemOcultaRepository ocultas;
    private final UsuarioRepository usuarios;
    private final ConfiguracaoService configuracoes;

    public ChatService(ChatMensagemRepository mensagens, ChatAnexoRepository anexos,
                       ChatMensagemOcultaRepository ocultas, UsuarioRepository usuarios,
                       ConfiguracaoService configuracoes) {
        this.mensagens = mensagens;
        this.anexos = anexos;
        this.ocultas = ocultas;
        this.usuarios = usuarios;
        this.configuracoes = configuracoes;
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

    /**
     * Contatos disponíveis para o usuário, conforme {@link #INTERLOCUTORES}.
     *
     * <p>Para o aluno, o professor que desligou "Perfil visível para alunos" vem sem a
     * foto (a tela cai no avatar padrão). Continua na lista: tirar o professor do chat
     * deixaria o aluno sem como falar com ele.
     */
    @Transactional(readOnly = true)
    public List<ContatoDTO> contatos(Long usuarioId) {
        Role meuPapel = usuarios.findRoleById(usuarioId).orElse(null);
        if (meuPapel == null) return List.of();
        List<Usuario> lista = usuarios.findByRoleInOrderByNome(INTERLOCUTORES.get(meuPapel)).stream()
                .filter(u -> !u.getId().equals(usuarioId))
                .toList();

        Set<Long> semFoto = meuPapel != Role.ALUNO ? Set.of()
                : configuracoes.comPerfilOcultoParaAlunos(lista.stream()
                        .filter(u -> u.getRole() == Role.PROFESSOR).map(Usuario::getId).toList());

        return lista.stream()
                .map(u -> new ContatoDTO(u.getId(), u.getNome(), u.getRole().name(),
                        semFoto.contains(u.getId()) ? null : u.getFoto()))
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

        // Metadados dos anexos de todas as mensagens numa query só (sem os bytes).
        Map<Long, List<AnexoDTO>> anexosPorMensagem = recentes.isEmpty() ? Map.of()
                : anexos.findByMensagemIdInOrderByCriadoEm(recentes.stream().map(ChatMensagem::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(ChatAnexoRepository.Metadados::getMensagemId,
                                LinkedHashMap::new,
                                Collectors.mapping(AnexoDTO::of, Collectors.toList())));

        // A query vem do mais novo para o mais antigo (é assim que se pega o fim da
        // lista no banco); a resposta precisa da ordem cronológica.
        List<ChatMensagemDTO> historico = new ArrayList<>(recentes.size());
        for (int i = recentes.size() - 1; i >= 0; i--) {
            ChatMensagem m = recentes.get(i);
            historico.add(ChatMensagemDTO.of(m, anexosPorMensagem.getOrDefault(m.getId(), List.of())));
        }
        return historico;
    }

    /** Mensagem só de texto (caminho do WebSocket). */
    @Transactional
    public ChatMensagemDTO salvar(Long remetenteId, String remetenteNome, Long destinatarioId, String texto) {
        String limpo = texto.trim();
        if (limpo.length() > LIMITE_TEXTO) limpo = limpo.substring(0, LIMITE_TEXTO);
        ChatMensagem m = new ChatMensagem(remetenteId, remetenteNome, destinatarioId, limpo, Instant.now());
        return ChatMensagemDTO.of(mensagens.save(m), List.of());
    }

    /**
     * Mensagem com anexos (arquivos e/ou áudio gravado), enviada por multipart. O texto
     * é opcional quando há anexo. Toda a validação acontece antes de gravar qualquer coisa.
     */
    @Transactional
    public ChatMensagemDTO salvarComAnexos(Long remetenteId, String remetenteNome, Long destinatarioId,
                                           String texto, List<MultipartFile> arquivos) {
        if (!podeConversar(remetenteId, destinatarioId)) {
            throw ApiException.forbidden("Você não pode conversar com este usuário.");
        }
        List<MultipartFile> validos = arquivos == null ? List.of()
                : arquivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        String limpo = texto == null ? "" : texto.trim();

        if (validos.isEmpty() && limpo.isEmpty()) {
            throw ApiException.badRequest("Envie um texto ou ao menos um arquivo.");
        }
        if (validos.size() > MAX_ANEXOS) {
            throw ApiException.badRequest("Envie no máximo " + MAX_ANEXOS + " arquivos por mensagem.");
        }
        if (limpo.length() > LIMITE_TEXTO) {
            throw ApiException.badRequest("A mensagem deve ter no máximo " + LIMITE_TEXTO + " caracteres.");
        }
        for (MultipartFile a : validos) {
            if (a.getSize() > TAMANHO_MAXIMO_ANEXO) {
                throw ApiException.badRequest("Cada arquivo deve ter no máximo 5 MB.");
            }
        }

        Instant agora = Instant.now();
        ChatMensagem m = mensagens.save(new ChatMensagem(remetenteId, remetenteNome, destinatarioId, limpo, agora));

        List<AnexoDTO> salvos = new ArrayList<>(validos.size());
        for (MultipartFile a : validos) {
            byte[] dados;
            try {
                dados = a.getBytes();
            } catch (Exception e) {
                throw ApiException.badRequest("Não foi possível ler o arquivo enviado.");
            }
            String id = UUID.randomUUID().toString().replace("-", "");
            ChatAnexo anexo = new ChatAnexo(id, m.getId(), nomeSeguro(a.getOriginalFilename()),
                    tipoSeguro(a.getContentType()), dados, agora);
            salvos.add(AnexoDTO.of(anexos.save(anexo)));
        }
        return ChatMensagemDTO.of(m, salvos);
    }

    /**
     * Apaga a mensagem (e os anexos) para os dois lados. Só o autor pode apagar.
     * Quem não participa da conversa recebe 404, para não confirmar que o id existe.
     */
    @Transactional
    public MensagemApagadaDTO apagar(Long usuarioId, Long mensagemId) {
        ChatMensagem m = mensagens.findById(mensagemId)
                .filter(x -> participa(usuarioId, x))
                .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada."));
        if (!m.getRemetenteId().equals(usuarioId)) {
            throw ApiException.forbidden("Só quem enviou pode apagar a mensagem.");
        }
        anexos.deleteByMensagemId(m.getId());
        ocultas.deleteByMensagemId(m.getId());
        mensagens.delete(m);
        return new MensagemApagadaDTO(m.getId(), m.getRemetenteId(), m.getDestinatarioId());
    }

    /**
     * "Apagar para mim": some só do histórico de quem pediu; o outro continua vendo.
     * Vale para mensagem própria ou recebida. Repetir o pedido não dá erro.
     */
    @Transactional
    public MensagemOcultadaDTO ocultar(Long usuarioId, Long mensagemId) {
        ChatMensagem m = mensagens.findById(mensagemId)
                .filter(x -> participa(usuarioId, x))
                .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada."));
        if (!ocultas.existsByUsuarioIdAndMensagemId(usuarioId, m.getId())) {
            ocultas.save(new ChatMensagemOculta(usuarioId, m.getId(), Instant.now()));
        }
        return new MensagemOcultadaDTO(m.getId(), m.getRemetenteId(), m.getDestinatarioId());
    }

    /** Bytes do anexo, se quem pede participa da conversa. */
    @Transactional(readOnly = true)
    public ConteudoAnexo anexo(Long usuarioId, String anexoId) {
        ChatAnexoRepository.Conteudo c = anexos.findConteudoById(anexoId)
                .orElseThrow(() -> ApiException.notFound("Arquivo não encontrado."));
        mensagens.findById(c.getMensagemId())
                .filter(m -> participa(usuarioId, m))
                // Quem apagou "para mim" também perde o acesso aos anexos da mensagem.
                .filter(m -> !ocultas.existsByUsuarioIdAndMensagemId(usuarioId, m.getId()))
                .orElseThrow(() -> ApiException.notFound("Arquivo não encontrado."));
        return new ConteudoAnexo(c.getNome(), c.getTipoConteudo(), c.getDados());
    }

    private static boolean participa(Long usuarioId, ChatMensagem m) {
        return usuarioId.equals(m.getRemetenteId()) || usuarioId.equals(m.getDestinatarioId());
    }

    /** Sem caminho (alguns navegadores mandam C:\fakepath\...), sem caracteres de controle e com tamanho limitado. */
    private static String nomeSeguro(String original) {
        String nome = original == null ? "" : original.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (nome.isEmpty()) nome = "arquivo";
        return nome.length() > 200 ? nome.substring(nome.length() - 200) : nome;
    }

    /** Tipo declarado pelo cliente, só se tiver formato de MIME; senão, binário genérico. */
    private static String tipoSeguro(String declarado) {
        if (declarado == null) return "application/octet-stream";
        String tipo = declarado.toLowerCase().trim();
        int pv = tipo.indexOf(';');
        if (pv >= 0) tipo = tipo.substring(0, pv).trim(); // "audio/webm;codecs=opus" → "audio/webm"
        return tipo.matches("[a-z0-9.+-]+/[a-z0-9.+-]+") ? tipo : "application/octet-stream";
    }
}
