package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;

/** Mensagem direta em tempo real entre dois usuários (chat professor ↔ diretor). */
@Entity
@Table(name = "chat_mensagens", indexes = {
        @Index(name = "idx_chat_remetente_destinatario", columnList = "remetente_id, destinatario_id"),
        @Index(name = "idx_chat_destinatario_remetente", columnList = "destinatario_id, remetente_id")
})
@Getter
public class ChatMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long remetenteId;

    private String remetenteNome;

    @Column(nullable = false)
    private Long destinatarioId;

    @Column(nullable = false, length = 2000)
    private String texto;

    private Instant criadaEm;

    public ChatMensagem() {}

    public ChatMensagem(Long remetenteId, String remetenteNome, Long destinatarioId, String texto, Instant criadaEm) {
        this.remetenteId = remetenteId;
        this.remetenteNome = remetenteNome;
        this.destinatarioId = destinatarioId;
        this.texto = texto;
        this.criadaEm = criadaEm;
    }
}
