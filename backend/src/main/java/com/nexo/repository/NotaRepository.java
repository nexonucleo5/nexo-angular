package com.nexo.repository;

import com.nexo.domain.Nota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotaRepository extends JpaRepository<Nota, Long> {

    @Query("""
           select n from Nota n
           where n.turma.id = :turmaId
             and (:disciplina is null or n.disciplina = :disciplina)
             and (:periodo is null or n.periodo = :periodo)
           order by n.aluno.nome
           """)
    List<Nota> buscarPorTurma(@Param("turmaId") Long turmaId,
                              @Param("disciplina") String disciplina,
                              @Param("periodo") String periodo);

    List<Nota> findByAlunoId(Long alunoId);

    /**
     * A nota exata que a edição altera. Existe a constraint única
     * (aluno_id, disciplina, periodo), então isso devolve no máximo uma linha —
     * antes o boletim inteiro do aluno era carregado só para filtrar em memória.
     */
    Optional<Nota> findByAlunoIdAndDisciplinaAndPeriodo(Long alunoId, String disciplina, String periodo);

    /**
     * Notas cruas de TODOS os alunos numa única query, para calcular médias em lote.
     * Evita o N+1 de chamar {@code findByAlunoId} por aluno em relatórios/evasão —
     * a média em si continua sendo calculada por {@link Nota#calcularMedia}, já que é
     * ponderada e arredondada por nota (não dá para reproduzir com {@code avg()} no SQL).
     */
    interface NotaBruta {
        Long getAlunoId();
        Double getP1();
        Double getP2();
        Double getT1();
        Double getParticipacao();
    }

    @Query("""
           select n.aluno.id as alunoId, n.p1 as p1, n.p2 as p2,
                  n.t1 as t1, n.participacao as participacao
           from Nota n
           """)
    List<NotaBruta> projetarTodas();

    @Query("""
           select n.aluno.id as alunoId, n.p1 as p1, n.p2 as p2,
                  n.t1 as t1, n.participacao as participacao
           from Nota n
           where n.periodo = :periodo
           """)
    List<NotaBruta> projetarPorPeriodo(@Param("periodo") String periodo);
}
