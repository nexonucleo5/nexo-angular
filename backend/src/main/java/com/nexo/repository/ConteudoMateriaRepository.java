package com.nexo.repository;

import com.nexo.domain.ConteudoMateria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConteudoMateriaRepository extends JpaRepository<ConteudoMateria, Long> {
    List<ConteudoMateria> findByMateriaIdOrderByOrdemAsc(Long materiaId);
    boolean existsByMateriaId(Long materiaId);

    /** (materiaId, total de conteúdos) — denominador do progresso, numa consulta só. */
    @org.springframework.data.jpa.repository.Query("""
           select c.materia.id, count(c) from ConteudoMateria c group by c.materia.id
           """)
    List<Object[]> totalPorMateria();
}
