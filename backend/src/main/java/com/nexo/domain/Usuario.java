package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
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

    /**
     * Ligada quando a senha foi sorteada pelo sistema (cadastro ou redefinição pelo
     * administrador) e, portanto, já passou pela mão de outra pessoa. O cliente lê a flag
     * no login e manda direto para a troca de senha; ela só desliga quando o próprio
     * usuário define uma senha nova.
     *
     * <p>{@code @ColumnDefault}: com {@code ddl-auto: update}, a coluna nasce em tabela já
     * povoada — sem default, o {@code NOT NULL} falharia nas linhas existentes.
     */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean precisaTrocarSenha = false;

    /**
     * Caixa de entrada real para onde vai o link de "esqueci minha senha". Opcional: o
     * e-mail institucional é só o login e não recebe correspondência. Sem este campo a
     * conta não tem recuperação automática e depende do administrador.
     */
    @Column(name = "email_recuperacao")
    private String emailRecuperacao;

    private Instant criadoEm = Instant.now();
}
