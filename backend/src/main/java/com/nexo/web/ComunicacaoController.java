package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.*;
import com.nexo.repository.*;
import com.nexo.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Mensagens, avisos e dúvidas — contrato "Comunicação" do arquitetura_java.md. */
@RestController
@org.springframework.transaction.annotation.Transactional
public class ComunicacaoController {

    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final AvisoRepository avisos;
    private final DuvidaRepository duvidas;

    public ComunicacaoController(ConversaRepository conversas, MensagemRepository mensagens,
                                 AvisoRepository avisos, DuvidaRepository duvidas) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.avisos = avisos;
        this.duvidas = duvidas;
    }

    // ── Mensagens ────────────────────────────────────────────────────────────

    public record MensagemDTO(Long id, String autor, boolean minha, String texto, Instant criadaEm) {
        static MensagemDTO of(Mensagem m) {
            return new MensagemDTO(m.getId(), m.getAutorNome(), m.isMinha(), m.getTexto(), m.getCriadaEm());
        }
    }

    public record ConversaDTO(Long id, String assunto, String participante, String papel,
                              Instant atualizadaEm, List<MensagemDTO> mensagens) {}

    @GetMapping("/api/mensagens")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public List<ConversaDTO> listar(@RequestParam(defaultValue = "entrada") String caixa) {
        Conversa.Caixa filtro = "enviada".equalsIgnoreCase(caixa) ? Conversa.Caixa.ENVIADA : Conversa.Caixa.ENTRADA;
        List<Conversa> lista = conversas.findByCaixaOrderByAtualizadaEmDesc(filtro);
        if (lista.isEmpty()) return List.of();

        // Todas as mensagens da caixa numa query só, agrupadas por conversa: antes era
        // uma consulta por conversa aberta na tela (N+1).
        Map<Long, List<MensagemDTO>> porConversa =
                mensagens.findByConversaIdInOrderByCriadaEmAsc(lista.stream().map(Conversa::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(m -> m.getConversa().getId(),
                                Collectors.mapping(MensagemDTO::of, Collectors.toList())));

        return lista.stream()
                .map(c -> new ConversaDTO(c.getId(), c.getAssunto(), c.getParticipanteNome(),
                        c.getParticipantePapel(), c.getAtualizadaEm(),
                        porConversa.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    /** A conversa só era alcançável dentro da listagem da caixa inteira. */
    @GetMapping("/api/mensagens/{conversaId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public ConversaDTO detalharConversa(@PathVariable Long conversaId) {
        Conversa c = conversas.findById(conversaId)
                .orElseThrow(() -> ApiException.notFound("Conversa não encontrada."));
        return new ConversaDTO(c.getId(), c.getAssunto(), c.getParticipanteNome(),
                c.getParticipantePapel(), c.getAtualizadaEm(),
                mensagens.findByConversaIdOrderByCriadaEmAsc(conversaId).stream()
                        .map(MensagemDTO::of).toList());
    }

    public record ResponderRequest(@NotBlank(message = "A mensagem não pode ser vazia.") String texto) {}

    /**
     * Sem Location de propósito: a mensagem criada aqui não tem recurso próprio
     * endereçável, e apontar para a conversa-pai identificaria outro recurso que
     * não o criado. Enquanto o envio for uma ação sobre a conversa, fica assim.
     */
    @PostMapping("/api/mensagens/{conversaId}/responder")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public MensagemDTO responder(@PathVariable Long conversaId,
                                 @Valid @RequestBody ResponderRequest request,
                                 @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Conversa conversa = conversas.findById(conversaId)
                .orElseThrow(() -> ApiException.notFound("Conversa não encontrada."));
        Mensagem mensagem = new Mensagem();
        mensagem.setConversa(conversa);
        mensagem.setAutorNome(usuario.nome());
        mensagem.setMinha(true);
        mensagem.setTexto(request.texto().trim());
        mensagem.setLida(true);
        conversa.setAtualizadaEm(Instant.now());
        conversas.save(conversa);
        return MensagemDTO.of(mensagens.save(mensagem));
    }

    // ── Avisos ───────────────────────────────────────────────────────────────

    public record AvisoDTO(Long id, String titulo, String conteudo, String autor, String destino, Instant criadoEm) {
        static AvisoDTO of(Aviso a) {
            return new AvisoDTO(a.getId(), a.getTitulo(), a.getConteudo(), a.getAutorNome(),
                    a.getDestino(), a.getCriadoEm());
        }
    }

    @GetMapping("/api/avisos")
    public List<AvisoDTO> listarAvisos() {
        return avisos.findAllByOrderByCriadoEmDesc().stream().map(AvisoDTO::of).toList();
    }

    @GetMapping("/api/avisos/{id}")
    public AvisoDTO detalharAviso(@PathVariable Long id) {
        return avisos.findById(id).map(AvisoDTO::of)
                .orElseThrow(() -> ApiException.notFound("Aviso não encontrado."));
    }

    public record NovoAvisoRequest(@NotBlank(message = "Informe o título do aviso.") String titulo,
                                   @NotBlank(message = "Informe o conteúdo do aviso.") String conteudo,
                                   String destino) {}

    @PostMapping("/api/avisos")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public ResponseEntity<AvisoDTO> publicarAviso(@Valid @RequestBody NovoAvisoRequest request,
                                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Aviso aviso = new Aviso();
        aviso.setTitulo(request.titulo().trim());
        aviso.setConteudo(request.conteudo().trim());
        aviso.setDestino(request.destino() != null ? request.destino() : "Todos");
        aviso.setAutorNome(usuario.nome());
        AvisoDTO dto = AvisoDTO.of(avisos.save(aviso));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(dto.id()).toUri()).body(dto);
    }

    // ── Dúvidas ──────────────────────────────────────────────────────────────

    public record DuvidaDTO(Long id, String aluno, String disciplina, String pergunta, String resposta,
                            String status, Instant criadaEm, Instant respondidaEm) {
        static DuvidaDTO of(Duvida d) {
            return new DuvidaDTO(d.getId(), d.getAluno().getNome(), d.getDisciplina(), d.getPergunta(),
                    d.getResposta(), d.getStatus().name(), d.getCriadaEm(), d.getRespondidaEm());
        }
    }

    @GetMapping("/api/duvidas")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public List<DuvidaDTO> listarDuvidas(@RequestParam(required = false) Duvida.Status status) {
        List<Duvida> lista = status != null
                ? duvidas.findByStatusOrderByCriadaEmDesc(status)
                : duvidas.findAllByOrderByCriadaEmDesc();
        return lista.stream().map(DuvidaDTO::of).toList();
    }

    public record ResponderDuvidaRequest(@NotBlank(message = "A resposta não pode ser vazia.") String texto) {}

    @PostMapping("/api/duvidas/{id}/responder")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public DuvidaDTO responderDuvida(@PathVariable Long id, @Valid @RequestBody ResponderDuvidaRequest request) {
        Duvida duvida = duvidas.findById(id)
                .orElseThrow(() -> ApiException.notFound("Dúvida não encontrada."));
        duvida.setResposta(request.texto().trim());
        duvida.setStatus(Duvida.Status.RESPONDIDA);
        duvida.setRespondidaEm(Instant.now());
        return DuvidaDTO.of(duvidas.save(duvida));
    }
}
