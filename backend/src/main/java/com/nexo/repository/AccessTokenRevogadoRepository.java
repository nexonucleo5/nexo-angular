package com.nexo.repository;

import com.nexo.domain.AccessTokenRevogado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AccessTokenRevogadoRepository extends JpaRepository<AccessTokenRevogado, String> {

    /** O que ainda vale revogado — lido uma vez, na subida, para repovoar a memória. */
    List<AccessTokenRevogado> findByRevogadoEmAfter(Instant limite);

    /** Revogações cuja janela já passou: o token correspondente expirou sozinho. */
    @Modifying
    @Query("delete from AccessTokenRevogado r where r.revogadoEm < :limite")
    int removerAnterioresA(@Param("limite") Instant limite);
}
