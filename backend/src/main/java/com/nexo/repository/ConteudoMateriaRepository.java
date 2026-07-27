package com.nexo.repository;

import com.nexo.domain.ConteudoMateria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConteudoMateriaRepository extends JpaRepository<ConteudoMateria, Long> {
    List<ConteudoMateria> findByMateriaIdOrderByOrdemAsc(Long materiaId);
    boolean existsByMateriaId(Long materiaId);
}
