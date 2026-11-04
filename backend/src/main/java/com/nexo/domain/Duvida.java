package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "duvidas")
@Getter
@Setter
public class Duvida {

    public enum Status { ABERTA, RESPONDIDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Aluno aluno;

    private String disciplina;

    @Column(nullable = false, length = 4000)
    private String pergunta;

    @Column(length = 4000)
    private String resposta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ABERTA;

    private Instant criadaEm = Instant.now();

    private Instant respondidaEm;
}
