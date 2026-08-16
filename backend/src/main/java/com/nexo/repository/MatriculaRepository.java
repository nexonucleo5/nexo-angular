package com.nexo.repository;

import com.nexo.domain.Matricula;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    /** {@code busca} deve chegar já como padrão LIKE em minúsculo (ex.: "%ana%") ou null. */
    @Query("""
           select m from Matricula m
           where (:status is null or m.status = :status)
             and (:turmaId is null or m.turma.id = :turmaId)
             and (:busca is null or lower(m.aluno.nome) like :busca)
           order by m.aluno.nome
           """)
    Page<Matricula> buscar(@Param("status") Matricula.Status status,
                           @Param("turmaId") Long turmaId,
                           @Param("busca") String busca,
                           Pageable pageable);

    long countByStatus(Matricula.Status status);

    long countByDocumentacao(Matricula.Documentacao documentacao);

    long countByDocumentacaoNot(Matricula.Documentacao documentacao);
}
