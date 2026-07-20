package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.*;
import com.nexo.repository.*;
import com.nexo.security.UsuarioAutenticado;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

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
    public List<ConversaDTO> listar(@RequestParam(defaultValue = "entrada") String caixa) {
        Conversa.Caixa filtro = "enviada".equalsIgnoreCase(caixa) ? Conversa.Caixa.ENVIADA : Conversa.Caixa.ENTRADA;
        return conversas.findByCaixaOrderByAtualizadaEmDesc(filtro).stream()
                .map(c -> new ConversaDTO(c.getId(), c.getAssunto(), c.getParticipanteNome(),
                        c.getParticipantePapel(), c.getAtualizadaEm(),
                        mensagens.findByConversaIdOrderByCriadaEmAsc(c.getId()).stream()
                                .map(MensagemDTO::of).toList()))
                .toList();
    }

    public record ResponderRequest(@NotBlank String texto) {}

    @PostMapping("/api/mensagens/{conversaId}/responder")
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemDTO responder(@PathVariable Long conversaId,
                                 @RequestBody ResponderRequest request,
                                 @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Conversa conversa = conversas.findById(conversaId)
                .orElseThrow(() -> ApiException.notFound("Conversa não encontrada."));
        if (request.texto() == null || request.texto().isBlank()) {
            throw ApiException.badRequest("A mensagem não pode ser vazia.");
        }
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

    public record NovoAvisoRequest(@NotBlank String titulo, @NotBlank String conteudo, String destino) {}

    @PostMapping("/api/avisos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public AvisoDTO publicarAviso(@RequestBody NovoAvisoRequest request,
                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (request.titulo() == null || request.titulo().isBlank()
                || request.conteudo() == null || request.conteudo().isBlank()) {
            throw ApiException.badRequest("Título e conteúdo são obrigatórios.");
        }
        Aviso aviso = new Aviso();
        aviso.setTitulo(request.titulo().trim());
        aviso.setConteudo(request.conteudo().trim());
        aviso.setDestino(request.destino() != null ? request.destino() : "Todos");
        aviso.setAutorNome(usuario.nome());
        return AvisoDTO.of(avisos.save(aviso));
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

    public record ResponderDuvidaRequest(@NotBlank String texto) {}

    @PostMapping("/api/duvidas/{id}/responder")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public DuvidaDTO responderDuvida(@PathVariable Long id, @RequestBody ResponderDuvidaRequest request) {
        Duvida duvida = duvidas.findById(id)
                .orElseThrow(() -> ApiException.notFound("Dúvida não encontrada."));
        if (request.texto() == null || request.texto().isBlank()) {
            throw ApiException.badRequest("A resposta não pode ser vazia.");
        }
        duvida.setResposta(request.texto().trim());
        duvida.setStatus(Duvida.Status.RESPONDIDA);
        duvida.setRespondidaEm(Instant.now());
        return DuvidaDTO.of(duvidas.save(duvida));
    }
}
