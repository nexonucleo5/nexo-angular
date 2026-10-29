package com.nexo.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.nexo.domain.LogAplicacao;
import com.nexo.repository.LogAplicacaoRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Leva o log da aplicação (WARN e ERROR, por padrão) para a tabela {@code logs_aplicacao},
 * onde o administrador o lê.
 *
 * <p>Quatro cuidados fazem dele seguro de manter ligado:
 * <ul>
 *   <li><b>Nunca atrasa quem loga.</b> O evento só entra numa fila; quem grava no banco é
 *       uma thread própria. Uma requisição que loga um WARN não espera o INSERT, e um
 *       banco fora do ar não a derruba — a falha é engolida aqui.</li>
 *   <li><b>Não gera log sobre si mesmo.</b> Se o INSERT falhar, o Hibernate loga um erro,
 *       que viria para cá, falharia de novo e assim por diante. Eventos emitidos pela thread
 *       de gravação são ignorados.</li>
 *   <li><b>Não grava segredo.</b> Mensagem e stack trace passam por {@link RedatorSegredos}
 *       antes de entrar na fila.</li>
 *   <li><b>Não deixa um loop encher a tabela.</b> A mesma mensagem, da mesma origem e no
 *       mesmo nível, entra no máximo {@code nexo.logs.max-por-minuto} vezes por minuto; o
 *       excedente é contado em {@link #descartados()} e aparece no resumo.</li>
 * </ul>
 */
@Component
public class LogAplicacaoAppender extends AppenderBase<ILoggingEvent> {

    private static final String THREAD_GRAVADORA = "nexo-log-gravador";
    private static final int CAPACIDADE_FILA = 1000;
    private static final int TETO_MENSAGEM = 1000;
    private static final int TETO_EXCECAO = 4000;
    private static final int LOTE = 50;

    private final LogAplicacaoRepository repositorio;
    private final RedatorSegredos redator;
    private final AlertaErroService alerta;
    private final Level nivelMinimo;
    private final int maxPorMinuto;

    private final LinkedBlockingQueue<LogAplicacao> fila = new LinkedBlockingQueue<>(CAPACIDADE_FILA);
    private final Map<String, long[]> janelas = new ConcurrentHashMap<>(); // chave -> {minuto, contagem}
    private final AtomicLong descartados = new AtomicLong();
    private volatile boolean ativo;
    private Thread gravadora;

    public LogAplicacaoAppender(LogAplicacaoRepository repositorio, RedatorSegredos redator,
                                AlertaErroService alerta,
                                @Value("${nexo.logs.nivel-minimo:WARN}") String nivelMinimo,
                                @Value("${nexo.logs.max-por-minuto:20}") int maxPorMinuto) {
        this.repositorio = repositorio;
        this.redator = redator;
        this.alerta = alerta;
        // Abaixo de INFO não há o que guardar: o enum de nível da tabela começa nele.
        Level pedido = Level.toLevel(nivelMinimo, Level.WARN);
        this.nivelMinimo = pedido.isGreaterOrEqual(Level.INFO) ? pedido : Level.INFO;
        this.maxPorMinuto = maxPorMinuto;
        setName("nexo-log-aplicacao");
    }

    @PostConstruct
    void iniciar() {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        setContext(raiz.getLoggerContext());
        ativo = true;
        gravadora = new Thread(this::gravarEnquantoAtivo, THREAD_GRAVADORA);
        gravadora.setDaemon(true);
        gravadora.start();
        start();
        raiz.addAppender(this);
    }

    @PreDestroy
    void encerrar() throws InterruptedException {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        raiz.detachAppender(this);
        stop();
        ativo = false;
        gravadora.interrupt();
        gravadora.join(2000);
    }

    /** Eventos que passaram da cota por minuto ou não couberam na fila, desde que a aplicação subiu. */
    public long descartados() {
        return descartados.get();
    }

    @Override
    protected void append(ILoggingEvent evento) {
        if (!evento.getLevel().isGreaterOrEqual(nivelMinimo)) return;
        if (THREAD_GRAVADORA.equals(Thread.currentThread().getName())) return;
        if (evento.getLoggerName().startsWith(LogAplicacaoAppender.class.getName())) return;

        LogAplicacao log = converter(evento);
        if (!dentroDaCota(log)) {
            descartados.incrementAndGet();
            return;
        }
        if (!fila.offer(log)) descartados.incrementAndGet();
    }

    private LogAplicacao converter(ILoggingEvent e) {
        LogAplicacao log = new LogAplicacao();
        log.setNivel(e.getLevel().isGreaterOrEqual(Level.ERROR) ? LogAplicacao.Nivel.ERROR
                : e.getLevel().isGreaterOrEqual(Level.WARN) ? LogAplicacao.Nivel.WARN
                : LogAplicacao.Nivel.INFO);
        log.setOrigem(truncar(e.getLoggerName(), 300));
        // Mascara ANTES de gravar: o que não entra na tabela não vaza dela. A leitura mascara de novo.
        log.setMensagem(truncar(redator.mascarar(e.getFormattedMessage()), TETO_MENSAGEM));
        if (e.getThrowableProxy() != null) {
            log.setExcecao(truncar(redator.mascarar(ThrowableProxyUtil.asString(e.getThrowableProxy())), TETO_EXCECAO));
        }
        Map<String, String> mdc = e.getMDCPropertyMap();
        String duracao = mdc.get(RequisicaoLogFilter.MDC_DURACAO);
        if (duracao != null && duracao.matches("\\d{1,9}")) log.setDuracaoMs(Integer.parseInt(duracao));
        log.setRequisicaoId(truncar(mdc.get(RequisicaoLogFilter.MDC_REQUISICAO), 40));
        log.setMetodo(truncar(mdc.get(RequisicaoLogFilter.MDC_METODO), 10));
        log.setCaminho(truncar(mdc.get(RequisicaoLogFilter.MDC_CAMINHO), 300));
        log.setUsuario(truncar(mdc.get(RequisicaoLogFilter.MDC_USUARIO), 255));
        log.setCriadoEm(Instant.ofEpochMilli(e.getTimeStamp()));
        return log;
    }

    private boolean dentroDaCota(LogAplicacao log) {
        if (maxPorMinuto <= 0) return true;
        if (janelas.size() > 1000) janelas.clear(); // chaves de mensagens variáveis não podem crescer sem fim
        long minuto = System.currentTimeMillis() / 60_000;
        String chave = log.getNivel() + "|" + log.getOrigem() + "|" + log.getMensagem();
        long[] janela = janelas.compute(chave, (k, atual) -> {
            if (atual == null || atual[0] != minuto) return new long[]{minuto, 1};
            atual[1]++;
            return atual;
        });
        return janela[1] <= maxPorMinuto;
    }

    private void gravarEnquantoAtivo() {
        List<LogAplicacao> lote = new ArrayList<>(LOTE);
        while (ativo || !fila.isEmpty()) {
            try {
                LogAplicacao primeiro = fila.poll(500, TimeUnit.MILLISECONDS);
                if (primeiro == null) continue;
                lote.add(primeiro);
                fila.drainTo(lote, LOTE - 1);
                repositorio.saveAll(lote);
                lote.forEach(alerta::notificar);
            } catch (InterruptedException ie) {
                if (!ativo && fila.isEmpty()) return;
            } catch (RuntimeException falha) {
                // Banco indisponível ou tabela ainda não criada: perde-se o lote, não a aplicação.
                descartados.addAndGet(lote.size());
            } finally {
                lote.clear();
            }
        }
    }

    private static String truncar(String valor, int max) {
        if (valor == null) return null;
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}
