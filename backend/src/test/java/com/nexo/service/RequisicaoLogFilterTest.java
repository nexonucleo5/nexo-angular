package com.nexo.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequisicaoLogFilterTest {

    private final ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RequisicaoLogFilter.class);
    private ListAppender<ILoggingEvent> capturados;

    @BeforeEach
    void ligar() {
        capturados = new ListAppender<>();
        capturados.start();
        logger.addAppender(capturados);
    }

    @AfterEach
    void desligar() {
        logger.detachAppender(capturados);
    }

    private MockHttpServletResponse chamar(RequisicaoLogFilter filtro, String rota, int status, long gastaMs) throws Exception {
        var req = new MockHttpServletRequest("GET", rota);
        req.setRequestURI(rota);
        var resp = new MockHttpServletResponse();
        filtro.doFilter(req, resp, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                try { Thread.sleep(gastaMs); } catch (InterruptedException ignorada) { Thread.currentThread().interrupt(); }
                ((MockHttpServletResponse) rs).setStatus(status);
            }
        });
        return resp;
    }

    @Test
    @DisplayName("lentidão vira WARN de mensagem constante, com a duração à parte para agrupar por rota")
    void lentidaoTemMensagemConstante() throws Exception {
        chamar(new RequisicaoLogFilter(5), "/api/relatorios/desempenho", 200, 30);
        chamar(new RequisicaoLogFilter(5), "/api/relatorios/desempenho", 200, 45);

        var lentas = capturados.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
        assertThat(lentas).hasSize(2);
        assertThat(lentas).extracting(ILoggingEvent::getFormattedMessage).containsOnly("Requisição lenta");
        assertThat(lentas).allSatisfy(e -> {
            assertThat(Integer.parseInt(e.getMDCPropertyMap().get(RequisicaoLogFilter.MDC_DURACAO))).isGreaterThanOrEqualTo(30);
            assertThat(e.getMDCPropertyMap().get(RequisicaoLogFilter.MDC_CAMINHO)).isEqualTo("/api/relatorios/desempenho");
        });
    }

    @Test
    @DisplayName("resposta rápida e 200 não geram log; fora de /api o filtro nem entra")
    void semRuido() throws Exception {
        chamar(new RequisicaoLogFilter(5000), "/api/algo", 200, 0);
        chamar(new RequisicaoLogFilter(1), "/index.html", 200, 10);
        assertThat(capturados.list).isEmpty();
    }

    @Test
    @DisplayName("toda resposta de /api leva X-Request-Id, e 401 não é aviso")
    void identificaARequisicao() throws Exception {
        var resp = chamar(new RequisicaoLogFilter(5000), "/api/algo", 401, 0);
        assertThat(resp.getHeader(RequisicaoLogFilter.CABECALHO)).isNotBlank();
        assertThat(capturados.list).as("401 é o ciclo normal de renovação do token").isEmpty();
    }
}
