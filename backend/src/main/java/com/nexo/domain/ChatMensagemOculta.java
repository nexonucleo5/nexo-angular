package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;

/**
 * "Apagar para mim": a mensagem continua existindo para o outro participante, mas
 * some do histórico de quem a ocultou. Uma linha por (usuário, mensagem).
 */
@Entity
@Table(name = "chat_mensagens_ocultas",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_oculta_usuario_mensagem",
                columnNames = {"usuario_id", "mensagem_id"}),
        indexes = @Index(name = "idx_chat_oculta_mensagem", columnList = "mensagem_id"))
@Getter
public class ChatMensagemOculta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "mensagem_id", nullable = false)
    private Long mensagemId;

    @Column(nullable = false)
    private Instant ocultadaEm;

    protected ChatMensagemOculta() {}

    public ChatMensagemOculta(Long usuarioId, Long mensagemId, Instant ocultadaEm) {
        this.usuarioId = usuarioId;
        this.mensagemId = mensagemId;
        this.ocultadaEm = ocultadaEm;
    }
}
