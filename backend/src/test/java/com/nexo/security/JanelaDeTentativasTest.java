package com.nexo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JanelaDeTentativasTest {

    private static final Duration JANELA = Duration.ofMinutes(15);

    @Test
    @DisplayName("bloqueia só ao atingir o máximo, e informa quanto falta")
    void bloqueiaNoMaximo() {
        JanelaDeTentativas janela = new JanelaDeTentativas(3, JANELA);

        janela.registrarFalha("ana");
        janela.registrarFalha("ana");
        assertThat(janela.segundosDeBloqueio("ana")).isZero();

        janela.registrarFalha("ana");
        assertThat(janela.segundosDeBloqueio("ana")).isPositive();
    }

    @Test
    @DisplayName("as chaves não interferem umas nas outras")
    void chavesSaoIndependentes() {
        JanelaDeTentativas janela = new JanelaDeTentativas(1, JANELA);

        janela.registrarFalha("ana");

        assertThat(janela.segundosDeBloqueio("ana")).isPositive();
        assertThat(janela.segundosDeBloqueio("bruno")).isZero();
    }

    @Test
    @DisplayName("acerto zera a contagem da chave")
    void limparLiberaAChave() {
        JanelaDeTentativas janela = new JanelaDeTentativas(1, JANELA);

        janela.registrarFalha("ana");
        janela.limpar("ana");

        assertThat(janela.segundosDeBloqueio("ana")).isZero();
    }

    @Test
    @DisplayName("máximo 0 desliga o limite — é como a configuração o desativa")
    void maximoZeroDesliga() {
        JanelaDeTentativas janela = new JanelaDeTentativas(0, JANELA);

        for (int i = 0; i < 50; i++) janela.registrarFalha("ana");

        assertThat(janela.desligada()).isTrue();
        assertThat(janela.segundosDeBloqueio("ana")).isZero();
    }

    @Test
    @DisplayName("chave nula não quebra nem bloqueia (origem desconhecida)")
    void chaveNulaEIgnorada() {
        JanelaDeTentativas janela = new JanelaDeTentativas(1, JANELA);

        janela.registrarFalha(null);

        assertThat(janela.segundosDeBloqueio(null)).isZero();
    }
}
