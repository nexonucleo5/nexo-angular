package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Mensagem direta em tempo real entre dois usuários (chat professor ↔ diretor). */
@Entity
@Table(name = "chat_mensagens", indexes = {
        @Index(name = "idx_chat_remetente_destinatario", columnList = "remetente_id, destinatario_id"),
        @Index(name = "idx_chat_destinatario_remetente", columnList = "destinatario_id, remetente_id")
})
public class ChatMensagem {

    /**
     * Teto do texto, em caracteres — o mesmo número que define a coluna abaixo.
     * Público porque quem recebe a mensagem (ChatWebSocketHandler) precisa recusar
     * o excesso <b>antes</b> de chegar aqui: o texto vem do cliente pelo WebSocket,
     * e passar do limite estourava o insert dentro do handler, o que derrubava a
     * conexão de quem estava conversando.
     */
    public static final int TAMANHO_MAXIMO_TEXTO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long remetenteId;

    private String remetenteNome;

    @Column(nullable = false)
    private Long destinatarioId;

    @Column(nullable = false, length = TAMANHO_MAXIMO_TEXTO)
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

    public Long getId() { return id; }
    public Long getRemetenteId() { return remetenteId; }
    public String getRemetenteNome() { return remetenteNome; }
    public Long getDestinatarioId() { return destinatarioId; }
    public String getTexto() { return texto; }
    public Instant getCriadaEm() { return criadaEm; }
}
