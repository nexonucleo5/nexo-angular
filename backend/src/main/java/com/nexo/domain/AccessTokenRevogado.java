package com.nexo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * Uma revogação de access token que precisa sobreviver a um reinício da aplicação.
 *
 * <p>A chave diz o que foi revogado: {@code jti:<id do token>} para o logout de uma sessão,
 * ou {@code uid:<id do usuário>} para "todo token emitido até agora" — conta desativada,
 * senha redefinida pelo administrador, sessão roubada detectada. O significado de cada
 * prefixo vive em {@code AccessTokensRevogados}; aqui é só o registro.
 *
 * <p>A linha só importa durante a vida útil de um access token (15 min por padrão): passada
 * a janela o JWT já expirou sozinho, e {@code AccessTokensRevogados#limparBanco} a apaga.
 */
@Entity
@Table(name = "access_tokens_revogados",
       indexes = @Index(name = "idx_access_revogados_em", columnList = "revogado_em"))
@Getter
public class AccessTokenRevogado {

    @Id
    @Column(length = 80)
    private String chave;

    @Column(name = "revogado_em", nullable = false)
    private Instant revogadoEm;

    protected AccessTokenRevogado() {}

    public AccessTokenRevogado(String chave, Instant revogadoEm) {
        this.chave = chave;
        this.revogadoEm = revogadoEm;
    }
}
