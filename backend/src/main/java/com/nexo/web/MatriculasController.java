package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.api.PageEnvelope;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Matricula;
import com.nexo.repository.MatriculaRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuditoriaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/matriculas")
@PreAuthorize("hasRole('DIRETOR')")
@org.springframework.transaction.annotation.Transactional
public class MatriculasController {

    private final MatriculaRepository matriculas;
    private final AuditoriaService auditoria;

    public MatriculasController(MatriculaRepository matriculas, AuditoriaService auditoria) {
        this.matriculas = matriculas;
        this.auditoria = auditoria;
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
}
