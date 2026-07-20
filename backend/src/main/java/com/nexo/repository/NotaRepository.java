package com.nexo.repository;

import com.nexo.domain.Nota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
