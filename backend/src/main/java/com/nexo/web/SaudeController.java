package com.nexo.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sonda de disponibilidade para monitor externo (UptimeRobot, health check do Render).
 *
 * <p>É público de propósito — o monitor não tem login — e por isso só diz {@code UP} ou
 * {@code DOWN}: nada de versão, memória, nome do banco ou latência, que ajudariam quem está
 * sondando. Esses detalhes ficam em {@code /api/auditoria/sistema/resumo}, atrás do ADMIN.
 *
 * <p>Responde 503 com o banco inacessível, e não 200 com um corpo "DOWN": monitores decidem
 * pelo código HTTP. Uma sonda que só testa "o processo está de pé" avisaria que está tudo
 * bem com o sistema inutilizável.
 */
@RestController
public class SaudeController {

    private final JdbcTemplate jdbc;

    public SaudeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/saude")
    public ResponseEntity<Map<String, String>> saude() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}
