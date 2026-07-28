package com.nexo.security;

import com.nexo.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Remove diariamente os refresh tokens já revogados ou vencidos — sem isso a
 * tabela cresce sem limite, já que a rotação de token nunca apaga o antigo,
 * só marca como revogado.
 */
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "0 0 3 * * *") // 03:00 — fora do horário de uso da escola
    @Transactional
    public void limpar() {
        refreshTokens.removerExpiradosOuRevogados(Instant.now());
    }
}
