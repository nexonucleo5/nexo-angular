package com.nexo.repository;

import com.nexo.domain.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AvisoRepository extends JpaRepository<Aviso, Long> {
    List<Aviso> findAllByOrderByCriadoEmDesc();

    /**
     * Avisos sem destino definido (valem para todos) mais os endereçados a um dos destinos
     * dados — nomes de turma e "Todos", em minúsculas. A comparação ignora caixa porque o
     * destino é texto livre gravado por quem publicou.
     */
    @Query("""
           select a from Aviso a
           where a.destino is null or trim(a.destino) = '' or lower(trim(a.destino)) in :destinos
           order by a.criadoEm desc
           """)
    List<Aviso> findVisiveis(@Param("destinos") Collection<String> destinos);
}
