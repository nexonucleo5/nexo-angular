package com.nexo.service;

import com.nexo.security.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Dá identidade a cada chamada de {@code /api} e registra as que merecem atenção.
 *
 * <p>O {@code X-Request-Id} sai na resposta e entra no MDC, então todo log emitido durante a
 * requisição carrega o mesmo identificador, a rota e o usuário (ver
 * {@link LogAplicacaoAppender}). Quando alguém reporta "deu erro às 14h", o administrador
 * acha o log pelo id em vez de adivinhar pela hora.
 *
 * <p>Registrado dentro da cadeia do Spring Security, logo depois do {@code JwtAuthFilter}
 * (ver {@code SecurityConfig}) — é isso que torna o usuário conhecido aqui. Por isso não é
 * um {@code @Component}: como bean, o Boot o registraria também fora da cadeia.
 *
 * <p>O que vira WARN:
 * <ul>
 *   <li><b>403</b> — alguém tentou o que o perfil não permite. Pode ser só um botão
 *       esquecido na tela, ou alguém sondando a API;</li>
 *   <li><b>429</b> — o limite de tentativas barrou alguém;</li>
 *   <li><b>lentidão</b> — resposta acima do limite configurado.</li>
 * </ul>
 * O 401 fica de fora de propósito: o access token dura 15 minutos, então um 401 seguido de
 * renovação é o funcionamento normal, não um sinal.
 *
 * <p>A mensagem é constante e o que varia (rota, usuário) vai no MDC. Assim a cota por
 * minuto do appender agrupa as repetições do mesmo problema em vez de tratar cada rota
 * como um assunto novo — e a URL com query string, que pode carregar ticket ou termo de
 * busca, nunca é gravada.
 */
public class RequisicaoLogFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    public static final String MDC_REQUISICAO = "requisicaoId";
    public static final String MDC_METODO = "metodo";
    public static final String MDC_CAMINHO = "caminho";
    public static final String MDC_USUARIO = "usuario";
    public static final String MDC_DURACAO = "duracaoMs";

    private static final Logger log = LoggerFactory.getLogger(RequisicaoLogFilter.class);

    private final long limiteLentoMs;

    public RequisicaoLogFilter(long limiteLentoMs) {
        this.limiteLentoMs = limiteLentoMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString().substring(0, 8);
        response.setHeader(CABECALHO, id);
        MDC.put(MDC_REQUISICAO, id);
        MDC.put(MDC_METODO, request.getMethod());
        MDC.put(MDC_CAMINHO, request.getRequestURI());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            MDC.put(MDC_USUARIO, u.login());
        }

        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            int status = response.getStatus();
            if (status == 403) {
                log.warn("Acesso negado (403)");
            } else if (status == 429) {
                log.warn("Muitas tentativas (429)");
            }
            if (limiteLentoMs > 0 && ms >= limiteLentoMs) {
                // Mensagem constante, duração no MDC: assim as lentas de uma mesma rota se agrupam.
                MDC.put(MDC_DURACAO, String.valueOf(ms));
                log.warn("Requisição lenta");
                MDC.remove(MDC_DURACAO);
            }
            MDC.remove(MDC_REQUISICAO);
            MDC.remove(MDC_METODO);
            MDC.remove(MDC_CAMINHO);
            MDC.remove(MDC_USUARIO);
        }
    }
}
