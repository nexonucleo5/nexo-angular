package com.nexo.repository;

import com.nexo.domain.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    @Query("""
           select a from Avaliacao a
           where (:turmaId is null or a.turma.id = :turmaId)
             and (:status is null or a.status = :status)
           order by a.data desc
           """)
    List<Avaliacao> buscar(@Param("turmaId") Long turmaId, @Param("status") Avaliacao.Status status);

    List<Avaliacao> findByStatusInOrderByDataAsc(List<Avaliacao.Status> status);
}
