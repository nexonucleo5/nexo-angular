package com.nexo.service;

import com.nexo.domain.Aluno;
import com.nexo.repository.AlunoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Risco de evasão calculado no servidor a partir de dados reais
 * (frequência, notas e engajamento) — substitui os arrays fixos de gestao-evasao.ts.
 */
@Service
public class EvasaoService {

    public enum Risco { BAIXO, MEDIO, ALTO }

    public record AlunoRiscoDTO(Long alunoId, String nome, String turma, String matricula,
                                double media, double percentualFaltas, int engajamento, Risco risco,
                                String foto, String motivoPrincipal, java.time.Instant ultimoAcessoEm,
                                int intervencoes, java.time.Instant ultimaIntervencaoEm) {}

    private final AlunoRepository alunos;
    private final AgregadosAcademicos agregados;

    public EvasaoService(AlunoRepository alunos, AgregadosAcademicos agregados) {
        this.alunos = alunos;
        this.agregados = agregados;
    }

    @Transactional(readOnly = true)
    public List<AlunoRiscoDTO> calcularRisco(Risco filtroRisco, Long turmaId, String busca) {
        var indices = agregados.carregar(null);
        return alunos.findAllComTurma().stream()
                .filter(a -> turmaId == null || (a.getTurma() != null && Objects.equals(a.getTurma().getId(), turmaId)))
                .filter(a -> busca == null || busca.isBlank()
                        || a.getNome().toLowerCase().contains(busca.trim().toLowerCase()))
                .map(a -> avaliar(a, indices))
                .filter(dto -> filtroRisco == null || dto.risco() == filtroRisco)
                .sorted((a, b) -> b.risco().compareTo(a.risco()))
                .toList();
    }

    /** Avalia vários alunos reaproveitando um único carregamento de agregados (2 queries no total). */
    @Transactional(readOnly = true)
    public List<AlunoRiscoDTO> avaliarTodos(List<Aluno> lista) {
        var indices = agregados.carregar(null);
        return lista.stream().map(a -> avaliar(a, indices)).toList();
    }

    /** Classificação pura: não toca no banco, só consulta os agregados já carregados. */
    public AlunoRiscoDTO avaliar(Aluno aluno, AgregadosAcademicos.Indices indices) {
        double media = indices.mediaOuZero(aluno.getId());
        double percentualFaltas = indices.percentualFaltas(aluno.getId());

        int engajamento = aluno.getEngajamento();
        Risco risco;
        if (percentualFaltas > 25 || (media > 0 && media < 5.0) || engajamento < 30) {
            risco = Risco.ALTO;
        } else if (percentualFaltas > 15 || (media > 0 && media < 6.5) || engajamento < 55) {
            risco = Risco.MEDIO;
        } else {
            risco = Risco.BAIXO;
        }

        return new AlunoRiscoDTO(aluno.getId(), aluno.getNome(),
                aluno.getTurma() != null ? aluno.getTurma().getNome() : null,
                String.format("2024%03d", aluno.getId()),
                Math.round(media * 10.0) / 10.0, percentualFaltas, engajamento, risco,
                aluno.getFoto(), motivoPrincipal(percentualFaltas, media, engajamento),
                aluno.getUltimoAcessoEm(), aluno.getIntervencoes(), aluno.getUltimaIntervencaoEm());
    }

    /** Motivo dominante do risco, derivado dos mesmos fatores usados na classificação. */
    private String motivoPrincipal(double percentualFaltas, double media, int engajamento) {
        if (percentualFaltas > 25) return "Baixa frequência";
        if (media > 0 && media < 5.0) return "Baixo desempenho acadêmico";
        if (engajamento < 30) return "Queda acentuada no engajamento";
        if (percentualFaltas > 15) return "Frequência irregular";
        if (media > 0 && media < 6.5) return "Notas abaixo da média";
        if (engajamento < 55) return "Engajamento em queda";
        return "Acompanhamento preventivo";
    }
}
