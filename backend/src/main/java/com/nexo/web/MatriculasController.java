package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.api.PageEnvelope;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Matricula;
import com.nexo.repository.MatriculaRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuditoriaService;
import com.nexo.service.DeclaracaoMatriculaPdf;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/matriculas")
@PreAuthorize("hasAnyRole('DIRETOR','SECRETARIA')")
@org.springframework.transaction.annotation.Transactional
public class MatriculasController {

    private final MatriculaRepository matriculas;
    private final com.nexo.repository.TurmaRepository turmas;
    private final com.nexo.repository.AlunoRepository alunos;
    private final AuditoriaService auditoria;
    private final DeclaracaoMatriculaPdf declaracaoPdf;
    private final com.nexo.service.ConfiguracaoService configuracoes;

    public MatriculasController(MatriculaRepository matriculas,
                                com.nexo.repository.TurmaRepository turmas,
                                com.nexo.repository.AlunoRepository alunos,
                                AuditoriaService auditoria,
                                DeclaracaoMatriculaPdf declaracaoPdf,
                                com.nexo.service.ConfiguracaoService configuracoes) {
        this.matriculas = matriculas;
        this.turmas = turmas;
        this.alunos = alunos;
        this.auditoria = auditoria;
        this.declaracaoPdf = declaracaoPdf;
        this.configuracoes = configuracoes;
    }

    public record MatriculaDTO(Long id, Long alunoId, String aluno, String turma,
                               String status, String documentacao, LocalDate dataMatricula) {
        static MatriculaDTO of(Matricula m) {
            return new MatriculaDTO(m.getId(), m.getAluno().getId(), m.getAluno().getNome(),
                    m.getTurma() != null ? m.getTurma().getNome() : null,
                    m.getStatus().name(), m.getDocumentacao().name(), m.getDataMatricula());
        }
    }

    @GetMapping
    public PageEnvelope<MatriculaDTO> listar(@RequestParam(required = false) Matricula.Status status,
                                             @RequestParam(required = false) Long turma,
                                             @RequestParam(required = false) String busca,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        String filtroBusca = (busca == null || busca.isBlank())
                ? null
                : "%" + busca.trim().toLowerCase() + "%";
        var resultado = matriculas.buscar(status, turma, filtroBusca, PageRequest.of(page, Math.min(size, 100)));
        return PageEnvelope.of(resultado, MatriculaDTO::of);
    }

    @GetMapping("/{id}")
    public MatriculaDTO detalhar(@PathVariable Long id) {
        return matriculas.findById(id).map(MatriculaDTO::of)
                .orElseThrow(() -> ApiException.notFound("Matrícula não encontrada."));
    }

    public record AtualizarDocumentosRequest(Matricula.Documentacao documentacao) {}

    @PatchMapping("/{id}/documentos")
    public MatriculaDTO atualizarDocumentos(@PathVariable Long id,
                                            @RequestBody AtualizarDocumentosRequest request,
                                            @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (request.documentacao() == null) {
            throw ApiException.badRequest("Informe o novo status de documentação.");
        }
        Matricula matricula = matriculas.findById(id)
                .orElseThrow(() -> ApiException.notFound("Matrícula não encontrada."));
        matricula.setDocumentacao(request.documentacao());
        if (request.documentacao() == Matricula.Documentacao.COMPLETA
                && matricula.getStatus() == Matricula.Status.PENDENTE) {
            matricula.setStatus(Matricula.Status.ATIVA);
        }
        matriculas.save(matricula);
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                "Documentação de matrícula atualizada",
                "Matrícula #" + id + " → " + request.documentacao(), null);
        return MatriculaDTO.of(matricula);
    }

    // ── Status (trancamento, cancelamento, reativação) ───────────────────────

    public record AtualizarStatusRequest(Matricula.Status status) {}

    /**
     * Transições permitidas por estado. CANCELADA é terminal: reativar um
     * cancelamento é rematrícula, não edição — passa por outro processo.
     */
    private static final java.util.Map<Matricula.Status, java.util.Set<Matricula.Status>> TRANSICOES =
            java.util.Map.of(
                    Matricula.Status.PENDENTE, java.util.Set.of(Matricula.Status.ATIVA, Matricula.Status.CANCELADA),
                    Matricula.Status.ATIVA, java.util.Set.of(Matricula.Status.TRANCADA, Matricula.Status.CANCELADA),
                    Matricula.Status.TRANCADA, java.util.Set.of(Matricula.Status.ATIVA, Matricula.Status.CANCELADA),
                    Matricula.Status.CANCELADA, java.util.Set.of());

    @PatchMapping("/{id}/status")
    public MatriculaDTO atualizarStatus(@PathVariable Long id,
                                        @RequestBody AtualizarStatusRequest request,
                                        @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (request.status() == null) {
            throw ApiException.badRequest("Informe o novo status da matrícula.");
        }
        Matricula matricula = matriculas.findById(id)
                .orElseThrow(() -> ApiException.notFound("Matrícula não encontrada."));
        Matricula.Status atual = matricula.getStatus();
        if (atual == request.status()) {
            return MatriculaDTO.of(matricula); // idempotente: repetir o PATCH não é erro
        }
        if (!TRANSICOES.get(atual).contains(request.status())) {
            throw ApiException.validation("Transição de status não permitida.",
                    java.util.Map.of("status", "Não é possível ir de " + atual + " para " + request.status() + "."));
        }
        matricula.setStatus(request.status());
        matriculas.save(matricula);
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                "Status de matrícula atualizado",
                "Matrícula #" + id + ": " + atual + " → " + request.status(), null);
        return MatriculaDTO.of(matricula);
    }

    // ── Transferência de turma ───────────────────────────────────────────────

    public record TransferirTurmaRequest(Long turmaId) {}

    /**
     * Move o aluno de turma — a matrícula e o cadastro do aluno andam juntos,
     * senão o diário de classe e a matrícula apontariam para turmas diferentes.
     */
    @PatchMapping("/{id}/turma")
    public MatriculaDTO transferirTurma(@PathVariable Long id,
                                        @RequestBody TransferirTurmaRequest request,
                                        @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (request.turmaId() == null) {
            throw ApiException.badRequest("Informe a turma de destino.");
        }
        Matricula matricula = matriculas.findById(id)
                .orElseThrow(() -> ApiException.notFound("Matrícula não encontrada."));
        if (matricula.getStatus() == Matricula.Status.CANCELADA) {
            throw ApiException.validation("Matrícula cancelada não pode ser transferida.",
                    java.util.Map.of("status", "Reative por rematrícula antes de transferir."));
        }
        var destino = turmas.findById(request.turmaId())
                .orElseThrow(() -> ApiException.notFound("Turma de destino não encontrada."));
        var origem = matricula.getTurma();
        if (origem != null && origem.getId().equals(destino.getId())) {
            return MatriculaDTO.of(matricula); // já está lá — repetir não é erro
        }
        long ocupadas = alunos.countByTurmaId(destino.getId());
        if (ocupadas >= destino.getCapacidade()) {
            throw ApiException.validation("A turma de destino está lotada.",
                    java.util.Map.of("turmaId", destino.getNome() + " já tem " + ocupadas
                            + " alunos para " + destino.getCapacidade() + " vagas."));
        }
        matricula.setTurma(destino);
        var aluno = matricula.getAluno();
        aluno.setTurma(destino);
        matriculas.save(matricula);
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                "Aluno transferido de turma",
                aluno.getNome() + ": " + (origem != null ? origem.getNome() : "sem turma")
                        + " → " + destino.getNome(), null);
        return MatriculaDTO.of(matricula);
    }

    // ── Declaração de matrícula (documento oficial da secretaria) ────────────

    /**
     * Só matrícula ATIVA gera declaração: o documento atesta vínculo vigente, e
     * emiti-lo para uma matrícula trancada ou pendente atestaria algo falso.
     */
    @GetMapping("/{id}/declaracao")
    public org.springframework.http.ResponseEntity<byte[]> declaracao(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        Matricula matricula = matriculas.findById(id)
                .orElseThrow(() -> ApiException.notFound("Matrícula não encontrada."));
        if (matricula.getStatus() != Matricula.Status.ATIVA) {
            throw ApiException.validation("Declaração disponível apenas para matrícula ativa.",
                    java.util.Map.of("status", "A matrícula está " + matricula.getStatus() + "."));
        }
        byte[] pdf = declaracaoPdf.gerar(matricula, validadeDeclaracaoDias(operador));
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ACESSO,
                "Declaração de matrícula emitida",
                "Matrícula #" + id + " — " + matricula.getAluno().getNome(), null);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"declaracao-matricula-" + id + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Validade configurada na seção Documentos do operador (secretaria). Diretor
     * não tem a seção — cai no padrão de 30 dias, o mesmo default do backend.
     */
    private int validadeDeclaracaoDias(UsuarioAutenticado operador) {
        try {
            Object valor = configuracoes.obter(operador.id())
                    .getOrDefault("documentos", java.util.Map.of())
                    .get("validadeDeclaracaoDias");
            if (valor instanceof Number n && n.intValue() > 0) return n.intValue();
        } catch (Exception ignorada) {
            // configuração ilegível não pode impedir a emissão do documento
        }
        return 30;
    }
}
