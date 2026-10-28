package com.nexo.service;

import com.nexo.repository.LogAplicacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Descarta logs da aplicação antigos — a mesma ideia de {@link AuditoriaRetencaoJob}, com
 * prazo bem menor: log de sistema serve para diagnosticar o que está acontecendo, não para
 * guardar histórico, e um erro de meses atrás já foi corrigido ou virou outra coisa.
 * Ajuste em {@code nexo.logs.retencao-dias}; 0 desliga a limpeza.
 */
@Component
public class LogsRetencaoJob {

    private static final Logger log = LoggerFactory.getLogger(LogsRetencaoJob.class);

    private final LogAplicacaoRepository logs;
    private final int retencaoDias;

    public LogsRetencaoJob(LogAplicacaoRepository logs,
                           @Value("${nexo.logs.retencao-dias:30}") int retencaoDias) {
        this.logs = logs;
        this.retencaoDias = retencaoDias;
    }

    @Scheduled(cron = "0 40 3 * * *") // 03:40 — depois da limpeza da auditoria
    @Transactional
    public void limpar() {
        if (retencaoDias <= 0) return;
        Instant corte = Instant.now().minus(retencaoDias, ChronoUnit.DAYS);
        int removidos = logs.removerAnterioresA(corte);
        if (removidos > 0) {
            log.info("Logs: {} registros anteriores a {} removidos.", removidos, corte);
        }
    }
}
