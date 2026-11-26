package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;

/**
 * Arquivo (ou áudio gravado) anexado a uma {@link ChatMensagem}. Até 3 por mensagem.
 *
 * <p>Em tabela própria pelo mesmo motivo de {@link FotoPerfil}: os bytes não viajam
 * junto com o histórico — a lista de mensagens leva só os metadados e cada anexo é
 * baixado sob demanda em /api/chat/anexos/{id}, que confere se quem pede participa
 * da conversa. O id é um UUID aleatório para não dar para varrer anexos por sequência.
 */
@Entity
@Table(name = "chat_anexos", indexes = {
        @Index(name = "idx_chat_anexo_mensagem", columnList = "mensagem_id")
})
@Getter
public class ChatAnexo {

    @Id
    private String id;

    @Column(name = "mensagem_id", nullable = false)
    private Long mensagemId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String tipoConteudo;

    @Column(nullable = false)
    private Long tamanho;

    /** bytea pelo mesmo motivo explicado em {@link FotoPerfil#getDados()}. */
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] dados;

    @Column(nullable = false)
    private Instant criadoEm;

    protected ChatAnexo() {}

    public ChatAnexo(String id, Long mensagemId, String nome, String tipoConteudo, byte[] dados, Instant criadoEm) {
        this.id = id;
        this.mensagemId = mensagemId;
        this.nome = nome;
        this.tipoConteudo = tipoConteudo;
        this.tamanho = (long) dados.length;
        this.dados = dados;
        this.criadoEm = criadoEm;
    }
}
