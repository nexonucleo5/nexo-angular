package com.nexo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Uma regra que vale para a escola inteira, e não para quem a configurou.
 *
 * <p>A configuração por usuário ({@link ConfiguracaoUsuario}) serve para preferência pessoal.
 * Controle não é preferência: "exigir aprovação de cadastro de aluno" guardado na conta de
 * cada diretor deixava a regra à escolha de quem cadastra — bastava um diretor com a opção
 * desligada para o controle não valer. Aqui há uma linha por regra, lida igual por todos.
 */
@Entity
@Table(name = "configuracoes_escola")
@Getter
@Setter
public class ConfiguracaoEscola {

    @Id
    @Column(length = 80)
    private String chave;

    @Column(nullable = false)
    private String valor;

    /** Quem mudou por último — a regra afeta todos, então o responsável precisa ser rastreável. */
    private String atualizadoPor;

    private Instant atualizadoEm = Instant.now();

    protected ConfiguracaoEscola() {}

    public ConfiguracaoEscola(String chave, String valor, String atualizadoPor) {
        this.chave = chave;
        this.valor = valor;
        this.atualizadoPor = atualizadoPor;
    }
}
