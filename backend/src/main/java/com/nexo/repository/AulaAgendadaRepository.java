package com.nexo.repository;

import com.nexo.domain.AulaAgendada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AulaAgendadaRepository extends JpaRepository<AulaAgendada, Long> {
    List<AulaAgendada> findTop6ByProfessorIdOrderByDataAscHoraAsc(Long professorId);
}
