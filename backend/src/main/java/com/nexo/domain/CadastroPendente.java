package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Pedido de cadastro de aluno aguardando aprovação do diretor.
 *
 * <p>Existe por causa da configuração "Exigir aprovação manual de cadastro de aluno":
 * ligada, o cadastro não cria conta. Ele só registra o pedido aqui, e o aluno, o
 * usuário de login e a inscrição nascem quando alguém aprova. É de propósito uma
 * tabela à parte, e não um aluno "inativo": um aluno pendente dentro de {@code alunos}
 * entraria em ranking, média de engajamento, risco de evasão e lotação de turma antes
 * de existir de fato.
 *
 * <p>A senha provisória também só é sorteada na aprovação — não há credencial guardada
 * ou exibida para uma conta que ainda não foi autorizada.
 */
@Entity
@Table(name = "cadastros_pendentes",
       // A fila que o diretor abre lista só os PENDENTE, e o painel conta os mesmos.
       indexes = @Index(name = "idx_cadastros_pendentes_situacao", columnList = "situacao"))
@Getter
@Setter
public class CadastroPendente {

    public enum Situacao { PENDENTE, APROVADO, REJEITADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Turma turma;

    /** E-mail de recuperação informado no pedido (já normalizado, opcional): vai para a conta quando aprovada. */
    private String emailRecuperacao;

    /** Nome de quem pediu o cadastro — para a trilha e para a fila. */
    @Column(nullable = false)
    private String solicitadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Situacao situacao = Situacao.PENDENTE;

    private Instant criadoEm = Instant.now();

    private String decididoPor;

    private Instant decididoEm;
}
