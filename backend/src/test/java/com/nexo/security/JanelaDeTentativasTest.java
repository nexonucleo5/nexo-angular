package com.nexo.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A peça de contagem por trás do bloqueio de login e do teto de requisições. Os
 * números reais (5 falhas por conta, 50 por origem, 120 requisições por minuto)
 * moram no AuthService; aqui se verifica o mecanismo, com limites pequenos para
 * o teste não depender de esperar quinze minutos nem de rodar bcrypt dezenas de
 * vezes.
 */
class JanelaDeTentativasTest {

    private static final Duration JANELA = Duration.ofMinutes(15);

    /**
     * O limite é "quantas passam": com limite 3, três ocorrências ainda deixam a chave
     * liberada e é a quarta consulta que barra. Consultar antes de registrar é o contrato
     * da classe — ver o javadoc de {@link JanelaDeTentativas#registrar}.
     */
    @Test
    void deixaPassarAteOLimite() {
        JanelaDeTentativas janela = new JanelaDeTentativas(3, JANELA);

        for (int i = 0; i < 3; i++) {
            assertThat(janela.bloqueioRestante("chave"))
                    .as("ocorrência %d ainda cabe no limite de 3", i + 1)
                    .isZero();
            janela.registrar("chave");
        }
    }

    @Test
    void bloqueiaDepoisDoLimiteEInformaOTempoRestante() {
        JanelaDeTentativas janela = new JanelaDeTentativas(3, JANELA);

        janela.registrar("chave");
        janela.registrar("chave");
        janela.registrar("chave");

        assertThat(janela.bloqueioRestante("chave"))
                .as("a quarta consulta encontra o limite alcançado")
                .isPositive()
                .isLessThanOrEqualTo(JANELA.toSeconds());
    }

    /**
     * O que separa "esta conta nesta origem" de "esta conta em qualquer origem" — e,
     * portanto, o que impede que o bloqueio de um atacante alcance o dono da conta.
     */
    @Test
    void chavesDiferentesContamSeparadamente() {
        JanelaDeTentativas janela = new JanelaDeTentativas(2, JANELA);

        janela.registrar("ana 203.0.113.7");
        janela.registrar("ana 203.0.113.7");

        assertThat(janela.bloqueioRestante("ana 203.0.113.7")).isPositive();
        assertThat(janela.bloqueioRestante("ana 127.0.0.1")).isZero();
    }

    @Test
    void limparLiberaAChave() {
        JanelaDeTentativas janela = new JanelaDeTentativas(1, JANELA);

        janela.registrar("chave");
        assertThat(janela.bloqueioRestante("chave")).isPositive();

        janela.limpar("chave");
        assertThat(janela.bloqueioRestante("chave")).isZero();
    }

    /**
     * Passada a janela a contagem recomeça, em vez de somar para sempre sobre um início
     * antigo — do contrário uma chave usada por tempo suficiente acabaria bloqueada por
     * ocorrências espalhadas por horas.
     */
    @Test
    void janelaVencidaRecomecaAContagem() throws InterruptedException {
        JanelaDeTentativas janela = new JanelaDeTentativas(2, Duration.ofMillis(50));

        janela.registrar("chave");
        janela.registrar("chave");
        assertThat(janela.bloqueioRestante("chave")).isPositive();

        Thread.sleep(80);

        assertThat(janela.bloqueioRestante("chave"))
                .as("janela vencida: a chave volta a ser aceita")
                .isZero();

        janela.registrar("chave");
        assertThat(janela.bloqueioRestante("chave"))
                .as("a contagem recomeçou — uma ocorrência não alcança o limite de 2")
                .isZero();
    }
}
