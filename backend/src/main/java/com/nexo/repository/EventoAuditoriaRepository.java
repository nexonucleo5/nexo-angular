package com.nexo.repository;

import com.nexo.domain.EventoAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {
    List<EventoAuditoria> findByCriadoEmAfterOrderByCriadoEmDesc(Instant desde);
    List<EventoAuditoria> findTop200ByOrderByCriadoEmDesc();
}
