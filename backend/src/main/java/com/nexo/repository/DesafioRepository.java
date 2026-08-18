package com.nexo.repository;

import com.nexo.domain.Desafio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DesafioRepository extends JpaRepository<Desafio, Long> {

    /** Catálogo do administrador: publicados e despublicados, agrupados por matéria. */
    List<Desafio> findAllByOrderByMateriaAscTituloAsc();

    long countByPublicado(Boolean publicado);
}
