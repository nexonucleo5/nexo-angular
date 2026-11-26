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

    /**
     * Por quanto tempo o último veredito vale. A rota é pública e cada chamada ocupava uma
     * conexão do pool (5 em produção) com um {@code select 1}: um laço de requisições
     * anônimas bastava para deixar as conexões ocupadas e o resto da API esperando. Com o
     * cache, o banco é consultado no máximo uma vez por janela, qualquer que seja o volume.
     * Um monitor que sonda a cada minuto continua vendo o estado real.
     */
    private static final long VALIDADE_MS = 5_000;

    private record Veredito(boolean up, long ate) {}

    private final JdbcTemplate jdbc;
    private volatile Veredito ultimo = new Veredito(false, 0);

    public SaudeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/saude")
    public ResponseEntity<Map<String, String>> saude() {
        Veredito v = ultimo;
        if (System.currentTimeMillis() >= v.ate()) {
            v = new Veredito(consultarBanco(), System.currentTimeMillis() + VALIDADE_MS);
            ultimo = v;
        }
        return v.up()
                ? ResponseEntity.ok(Map.of("status", "UP"))
                : ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }

    private boolean consultarBanco() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
