package com.nexo.web;

import com.nexo.api.PageEnvelope;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.LogAplicacao;
import com.nexo.repository.EventoAuditoriaRepository;
import com.nexo.repository.LogAplicacaoRepository;
import com.nexo.security.ClienteIp;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AlertaErroService;
import com.nexo.service.AuditoriaService;
import com.nexo.service.LogAplicacaoAppender;
import com.nexo.service.RedatorSegredos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Logs e saúde da aplicação — leitura exclusiva do administrador, como a auditoria
 * ({@link AuditoriaController}). Um stack trace revela estrutura interna e, às vezes,
 * valores de dados; não é coisa para o diretor nem para quem usa o sistema.
 *
 * <p>Mesmo sendo só do ADMIN, nada que saia daqui é cru: o texto passa pelo
 * {@link RedatorSegredos}, para que uma conta de administrador comprometida não entregue
 * senha de banco, segredo do JWT ou token de ninguém. E a própria leitura fica na auditoria.
 */
@RestController
@RequestMapping("/api/auditoria/sistema")
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class LogsSistemaController {

    private static final int TAMANHO_MAXIMO = 100;

    /** Uma consulta por usuário a cada 10 minutos entra na auditoria (ver registrarConsulta). */
    private static final java.time.Duration JANELA_DE_CONSULTA = java.time.Duration.ofMinutes(10);

    private final LogAplicacaoRepository logs;
    private final LogAplicacaoAppender appender;
    private final AlertaErroService alertas;
    private final JdbcTemplate jdbc;
    private final RedatorSegredos redator;
    private final EventoAuditoriaRepository eventos;
    private final AuditoriaService auditoria;
    private final ClienteIp clienteIp;

    public LogsSistemaController(LogAplicacaoRepository logs, LogAplicacaoAppender appender,
                                 AlertaErroService alertas, JdbcTemplate jdbc,
                                 RedatorSegredos redator, EventoAuditoriaRepository eventos,
                                 AuditoriaService auditoria, ClienteIp clienteIp) {
        this.logs = logs;
        this.appender = appender;
        this.alertas = alertas;
        this.jdbc = jdbc;
        this.redator = redator;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.clienteIp = clienteIp;
    }

    public record LogDTO(Long id, String nivel, String origem, String mensagem, String excecao,
                         String requisicaoId, String metodo, String caminho, String usuario,
                         Integer duracaoMs, Instant criadoEm) {
        /**
         * Mascara de novo na saída. O appender já o fez ao gravar, mas uma regra acrescentada
         * depois não alcança o que já está na tabela — e a tela é o último lugar onde ainda
         * dá para impedir que um segredo chegue a quem está olhando.
         */
        static LogDTO of(LogAplicacao l, RedatorSegredos redator) {
            return new LogDTO(l.getId(), l.getNivel().name(), l.getOrigem(),
                    redator.mascarar(l.getMensagem()), redator.mascarar(l.getExcecao()),
                    l.getRequisicaoId(), l.getMetodo(), l.getCaminho(),
                    l.getUsuario(), l.getDuracaoMs(), l.getCriadoEm());
        }
    }

    /**
     * @param nivel   INFO, WARN ou ERROR; ausente traz todos
     * @param busca   trecho da mensagem, origem, rota, usuário, id da requisição ou exceção
     * @param periodo últimos N dias; ausente ou não positivo não limita
     */
    @GetMapping("/logs")
    @Transactional // grava a própria consulta na auditoria; a classe é readOnly
    public PageEnvelope<LogDTO> listar(@RequestParam(required = false) LogAplicacao.Nivel nivel,
                                       @RequestParam(required = false) String busca,
                                       @RequestParam(required = false) Integer periodo,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size,
                                       @AuthenticationPrincipal UsuarioAutenticado operador,
                                       HttpServletRequest http) {
        // Quem leu os logs também fica registrado, uma vez por sessão de consulta. Se a conta
        // de um admin for usada indevidamente, é este o rastro que sobra.
        auditoria.registrarConsulta(operador.nome(), "Logs do sistema consultados",
                clienteIp.de(http), JANELA_DE_CONSULTA);

        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim().toLowerCase() + "%";
        Instant desde = (periodo == null || periodo <= 0)
                ? Instant.EPOCH
                : Instant.now().minus(periodo, ChronoUnit.DAYS);
        var pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO));
        return PageEnvelope.of(logs.buscar(nivel, filtro, desde, pagina), l -> LogDTO.of(l, redator));
    }

    public record ProblemaDTO(String nivel, String origem, String mensagem, String metodo, String caminho,
                              long total, Instant ultimaVez) {}

    /** Erros e avisos de uma hora cheia; {@code hora} é o início dela. */
    public record HoraDTO(Instant hora, long erros, long avisos) {}

    public record ResumoDTO(long erros24h, long avisos24h, Instant ultimoErro, long logsDescartados,
                            long falhasDeAcesso24h, List<ProblemaDTO> principaisProblemas,
                            List<HoraDTO> porHora,
                            boolean alertaAtivo,
                            boolean bancoOk, long bancoMs,
                            long heapUsadoMb, long heapMaxMb, long uptimeSegundos) {}

    /**
     * 24 baldes de uma hora, do mais antigo ao mais recente, sempre completos — hora sem
     * ocorrência vem zerada, para o gráfico mostrar o silêncio e não pular horas.
     * Agrupado aqui e não no banco: truncar por hora não é portável entre H2 e PostgreSQL, e
     * o volume é pequeno (o appender já limita repetições por minuto).
     */
    private List<HoraDTO> porHora(Instant inicio) {
        long[] erros = new long[24];
        long[] avisos = new long[24];
        for (Object[] o : logs.ocorrenciasDesde(inicio)) {
            int i = (int) ChronoUnit.HOURS.between(inicio, ((Instant) o[0]).truncatedTo(ChronoUnit.HOURS));
            if (i < 0 || i > 23) continue;
            if (o[1] == LogAplicacao.Nivel.ERROR) erros[i]++; else avisos[i]++;
        }
        List<HoraDTO> horas = new java.util.ArrayList<>(24);
        for (int i = 0; i < 24; i++) horas.add(new HoraDTO(inicio.plus(i, ChronoUnit.HOURS), erros[i], avisos[i]));
        return horas;
    }

    /** O que olhar primeiro: há erro novo? o que mais se repete? o banco responde? a memória aguenta? */
    @GetMapping("/resumo")
    public ResumoDTO resumo() {
        // 24 horas cheias, da hora de 23h atrás até a hora corrente. Contadores, ranking e gráfico
        // usam a MESMA janela: com limites diferentes, o card diria "2 erros" e o gráfico mostraria 1.
        Instant ultimas24h = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(23, ChronoUnit.HOURS);
        Map<LogAplicacao.Nivel, Long> contagem = new EnumMap<>(LogAplicacao.Nivel.class);
        for (Object[] linha : logs.contarPorNivelDesde(ultimas24h)) {
            contagem.put((LogAplicacao.Nivel) linha[0], ((Number) linha[1]).longValue());
        }

        boolean bancoOk = true;
        long inicio = System.nanoTime();
        try {
            jdbc.queryForObject("select 1", Integer.class);
        } catch (RuntimeException e) {
            bancoOk = false;
        }
        long bancoMs = (System.nanoTime() - inicio) / 1_000_000;

        var memoria = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var runtime = ManagementFactory.getRuntimeMXBean();

        List<ProblemaDTO> principais = logs.principaisProblemasDesde(ultimas24h, PageRequest.of(0, 5)).stream()
                .map(l -> new ProblemaDTO(((LogAplicacao.Nivel) l[0]).name(), (String) l[1],
                        redator.mascarar((String) l[2]), (String) l[3], (String) l[4],
                        ((Number) l[5]).longValue(), (Instant) l[6]))
                .toList();

        return new ResumoDTO(
                contagem.getOrDefault(LogAplicacao.Nivel.ERROR, 0L),
                contagem.getOrDefault(LogAplicacao.Nivel.WARN, 0L),
                logs.ultimoDoNivel(LogAplicacao.Nivel.ERROR),
                appender.descartados(),
                // Logins inválidos e reuso de refresh token: o termômetro de tentativa de invasão.
                eventos.countByTipoAndCriadoEmAfter(EventoAuditoria.Tipo.ERRO, ultimas24h),
                principais,
                porHora(ultimas24h),
                alertas.ativo(),
                bancoOk, bancoMs,
                memoria.getUsed() / (1024 * 1024),
                memoria.getMax() / (1024 * 1024),
                runtime.getUptime() / 1000);
    }
}
