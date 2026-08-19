package com.nexo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O ponto delicado: o X-Forwarded-For começa com o que o cliente mandou e termina com o
 * que o proxy acrescentou, então a leitura confiável é da direita para a esquerda.
 */
class ClienteIpTest {

    private static final String PEER = "10.0.0.7";

    private static MockHttpServletRequest requisicao(String forwardedFor) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(PEER);
        if (forwardedFor != null) req.addHeader("X-Forwarded-For", forwardedFor);
        return req;
    }

    @Test
    @DisplayName("sem proxy configurado o cabeçalho é ignorado por completo")
    void semProxyIgnoraCabecalho() {
        assertThat(new ClienteIp(0).de(requisicao("1.2.3.4"))).isEqualTo(PEER);
    }

    @Test
    @DisplayName("com um proxy, o endereço confiável é o último da lista")
    void umProxyPegaOUltimo() {
        assertThat(new ClienteIp(1).de(requisicao("203.0.113.10"))).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("valor forjado pelo cliente fica à esquerda e é descartado")
    void valorForjadoNaoVence() {
        // O cliente mandou "1.2.3.4"; o proxy anexou o endereço real depois dele.
        assertThat(new ClienteIp(1).de(requisicao("1.2.3.4, 203.0.113.10")))
                .isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("dois saltos confiáveis recuam duas posições")
    void doisSaltosRecuamDuas() {
        assertThat(new ClienteIp(2).de(requisicao("1.2.3.4, 203.0.113.10, 198.51.100.5")))
                .isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("lista mais curta que o esperado cai no item mais à esquerda")
    void listaCurtaNaoEstoura() {
        assertThat(new ClienteIp(3).de(requisicao("203.0.113.10"))).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("requisição que não passou pelo proxy cai no peer da conexão")
    void semCabecalhoUsaOPeer() {
        assertThat(new ClienteIp(1).de(requisicao(null))).isEqualTo(PEER);
    }
}
