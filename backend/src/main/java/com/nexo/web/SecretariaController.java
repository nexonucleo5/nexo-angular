package com.nexo.web;

import com.nexo.domain.Matricula;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.MatriculaRepository;
import com.nexo.repository.TurmaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
