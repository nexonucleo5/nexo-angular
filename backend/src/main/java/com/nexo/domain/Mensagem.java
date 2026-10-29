package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mensagens",
       indexes = @Index(name = "idx_mensagens_conversa_criada", columnList = "conversa_id, criada_em"))
@Getter
@Setter
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Conversa conversa;

    @Column(nullable = false)
    private String autorNome;

    /** true quando a mensagem foi enviada pelo usuário logado (lado direito do chat). */
    private boolean minha;

    @Column(nullable = false, length = 4000)
    private String texto;

    private Instant criadaEm = Instant.now();

    private boolean lida;
}
