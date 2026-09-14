package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    private String cargo;

    private String foto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private boolean ativo = true;

    private Instant criadoEm = Instant.now();
}
