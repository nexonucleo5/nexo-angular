package com.nexo.repository;

import com.nexo.domain.Frequencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FrequenciaRepository extends JpaRepository<Frequencia, Long> {
    List<Frequencia> findByTurmaIdAndData(Long turmaId, LocalDate data);

    /** Todas as chamadas da turma — base do percentual de presença por aluno. */
    List<Frequencia> findByTurmaId(Long turmaId);
    Optional<Frequencia> findByAlunoIdAndTurmaIdAndData(Long alunoId, Long turmaId, LocalDate data);
    long countByAlunoIdAndPresenteFalse(Long alunoId);
    long countByAlunoId(Long alunoId);
    long countByPresenteFalse();
    long count();

    /**
     * Total de aulas e de faltas por aluno, agregado no banco numa única query —
     * substitui o par {@code countByAlunoId}/{@code countByAlunoIdAndPresenteFalse}
     * chamado dentro de laços. Alunos sem nenhum registro não aparecem no resultado.
     */
    interface FrequenciaResumo {
        Long getAlunoId();
        long getTotal();
        long getFaltas();
    }

    @Query("""
           select f.aluno.id as alunoId, count(f) as total,
                  sum(case when f.presente = false then 1L else 0L end) as faltas
           from Frequencia f
           group by f.aluno.id
           """)
    List<FrequenciaResumo> resumoPorAluno();
}
