package com.nexo.repository;

import com.nexo.domain.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvisoRepository extends JpaRepository<Aviso, Long> {
    List<Aviso> findAllByOrderByCriadoEmDesc();
}
