package com.nexo.web;

import com.nexo.domain.Matricula;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.MatriculaRepository;
import com.nexo.repository.TurmaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Painel da secretaria: os números que orientam o trabalho do dia — quantas
 * matrículas aguardam efetivação e quantas têm documentação para cobrar.
 * O diretor também enxerga (supervisiona a secretaria), igual ao acesso
 * compartilhado que já existe em /api/matriculas.
 */
@RestController
@RequestMapping("/api/secretaria")
@PreAuthorize("hasAnyRole('SECRETARIA','DIRETOR')")
@Transactional(readOnly = true)
public class SecretariaController {

    private final MatriculaRepository matriculas;
    private final AlunoRepository alunos;
    private final TurmaRepository turmas;

    public SecretariaController(MatriculaRepository matriculas, AlunoRepository alunos, TurmaRepository turmas) {
        this.matriculas = matriculas;
        this.alunos = alunos;
        this.turmas = turmas;
    }

    public record DashboardSecretariaDTO(long totalAlunos, long totalTurmas,
                                         long matriculasAtivas, long matriculasPendentes,
                                         long matriculasTrancadas, long documentacaoPendente) {}

    @GetMapping("/dashboard")
    public DashboardSecretariaDTO dashboard() {
        return new DashboardSecretariaDTO(
                alunos.count(),
                turmas.count(),
                matriculas.countByStatus(Matricula.Status.ATIVA),
                matriculas.countByStatus(Matricula.Status.PENDENTE),
                matriculas.countByStatus(Matricula.Status.TRANCADA),
                matriculas.countByDocumentacaoNot(Matricula.Documentacao.COMPLETA));
    }

    // ── Fila de trabalho ─────────────────────────────────────────────────────

    /** O que travou cada item: matrícula por efetivar, documentação por cobrar, ou os dois. */
    public record PendenciaDTO(Long matriculaId, Long alunoId, String aluno, String turma,
                               String status, String documentacao, LocalDate dataMatricula,
                               boolean aguardaEfetivacao, boolean aguardaDocumentacao) {
        static PendenciaDTO of(Matricula m) {
            return new PendenciaDTO(m.getId(), m.getAluno().getId(), m.getAluno().getNome(),
                    m.getTurma() != null ? m.getTurma().getNome() : null,
                    m.getStatus().name(), m.getDocumentacao().name(), m.getDataMatricula(),
                    m.getStatus() == Matricula.Status.PENDENTE,
                    m.getDocumentacao() != Matricula.Documentacao.COMPLETA);
        }
    }

    @GetMapping("/pendencias")
    public List<PendenciaDTO> pendencias(@RequestParam(defaultValue = "8") int limite) {
        return matriculas.pendencias(PageRequest.of(0, Math.min(Math.max(limite, 1), 50)))
                .stream().map(PendenciaDTO::of).toList();
    }

    // ── Ocupação de turmas ───────────────────────────────────────────────────

    public record OcupacaoTurmaDTO(Long turmaId, String turma, String turno,
                                   long alunos, int capacidade, int percentual) {}

    /** Vagas por turma — o dado que a secretaria olha antes de matricular ou transferir. */
    @GetMapping("/turmas/ocupacao")
    public List<OcupacaoTurmaDTO> ocupacao() {
        Map<Long, Long> porTurma = new HashMap<>();
        for (Object[] linha : alunos.totalPorTurma()) {
            porTurma.put((Long) linha[0], (Long) linha[1]);
        }
        return turmas.findAll().stream()
                .map(t -> {
                    long ocupados = porTurma.getOrDefault(t.getId(), 0L);
                    int capacidade = t.getCapacidade();
                    int percentual = capacidade > 0 ? (int) Math.round(ocupados * 100.0 / capacidade) : 0;
                    return new OcupacaoTurmaDTO(t.getId(), t.getNome(), t.getTurno(),
                            ocupados, capacidade, percentual);
                })
                .sorted(java.util.Comparator.comparing(OcupacaoTurmaDTO::turma))
                .toList();
    }
}
