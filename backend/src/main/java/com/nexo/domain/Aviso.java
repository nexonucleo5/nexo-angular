package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "avisos")
@Getter
@Setter
public class Aviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 4000)
    private String conteudo;

    private String autorNome;

    /** Público-alvo: "Todos", nome de turma, etc. */
    private String destino;

    private Instant criadoEm = Instant.now();
}
