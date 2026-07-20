package com.nexo.repository;

import com.nexo.domain.Duvida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DuvidaRepository extends JpaRepository<Duvida, Long> {
    List<Duvida> findByStatusOrderByCriadaEmDesc(Duvida.Status status);
    List<Duvida> findAllByOrderByCriadaEmDesc();
}
