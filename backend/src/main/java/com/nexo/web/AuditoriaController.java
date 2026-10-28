package com.nexo.web;

import com.nexo.api.PageEnvelope;
import com.nexo.domain.EventoAuditoria;
import com.nexo.repository.EventoAuditoriaRepository;
import com.nexo.security.ClienteIp;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Logs do sistema — leitura exclusiva do administrador.
 *
 * <p>A trilha guarda quem entrou, de qual IP, quem errou a senha e o que foi alterado.
 * É informação sobre as pessoas, não sobre o conteúdo, e por isso o papel é só ADMIN:
 * nem o DIRETOR, que lê o resto do painel administrativo, passa daqui (ver
 * {@code AdminController}, que o admite). A tela é somente leitura — não há como
 * editar nem apagar evento; a única remoção é a rotina de retenção
 * ({@code AuditoriaRetencaoJob}).
 */
@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AuditoriaController {

    private static final int TAMANHO_MAXIMO = 100;

    /** Uma consulta por usuário a cada 10 minutos entra na auditoria (ver registrarConsulta). */
    private static final java.time.Duration JANELA_DE_CONSULTA = java.time.Duration.ofMinutes(10);

    private final EventoAuditoriaRepository eventos;
    private final AuditoriaService auditoria;
    private final ClienteIp clienteIp;

    public AuditoriaController(EventoAuditoriaRepository eventos, AuditoriaService auditoria,
                               ClienteIp clienteIp) {
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.clienteIp = clienteIp;
    }

    public record EventoDTO(Long id, String usuario, String tipo, String acao, String detalhe,
                            String ip, Instant criadoEm) {
        static EventoDTO of(EventoAuditoria e) {
            return new EventoDTO(e.getId(), e.getUsuarioNome(), e.getTipo().name(), e.getAcao(),
                    e.getDetalhe(), e.getIp(), e.getCriadoEm());
        }
    }

    /**
     * @param tipo    LOGIN, LOGOUT, ACESSO, ALTERACAO ou ERRO; ausente traz todos
     * @param busca   trecho do usuário, da ação, do detalhe ou do IP
     * @param periodo últimos N dias; ausente ou não positivo não limita
     */
    @GetMapping("/eventos")
    @Transactional // grava a própria consulta; a classe é readOnly
    public PageEnvelope<EventoDTO> eventos(@RequestParam(required = false) EventoAuditoria.Tipo tipo,
                                           @RequestParam(required = false) String busca,
                                           @RequestParam(required = false) Integer periodo,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size,
                                           @AuthenticationPrincipal UsuarioAutenticado operador,
                                           HttpServletRequest http) {
        auditoria.registrarConsulta(operador.nome(), "Trilha de auditoria consultada",
                clienteIp.de(http), JANELA_DE_CONSULTA);
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim().toLowerCase() + "%";
        Instant desde = (periodo == null || periodo <= 0)
                ? Instant.EPOCH
                : Instant.now().minus(periodo, ChronoUnit.DAYS);
        var pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO));
        return PageEnvelope.of(eventos.buscar(tipo, filtro, desde, pagina), EventoDTO::of);
    }
}
