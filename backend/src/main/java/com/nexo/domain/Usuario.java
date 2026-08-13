package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "usuarios")
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

    /**
     * Endereço que recebe as mensagens <b>da conta</b>: convite de primeiro acesso e
     * redefinição de senha.
     *
     * <p>Não confundir com o {@code login}, que para aluno e professor é o "e-mail
     * institucional" gerado pelo {@code CredenciaisService} num domínio de fachada
     * ({@code nexo.escola.com}): aquilo é identificador de acesso, não caixa de correio —
     * mandar mensagem para lá não chega a lugar nenhum.
     *
     * <p><b>De quem é este endereço.</b> De quem entra no sistema. Aluno de ensino médio
     * tem o próprio e-mail e é ele quem esquece a própria senha — obrigá-lo a pedir o link
     * ao responsável e esperar o repasse é atrito sem ganho de segurança, e ainda faz a
     * credencial dele passar pela caixa de outra pessoa. O endereço do responsável entra
     * aqui só quando o aluno é novo demais para ter um.
     *
     * <p><b>O que este campo não é.</b> Não é o canal para a escola falar <em>sobre</em> o
     * aluno com a família — nota, falta, alerta de evasão. Essa é outra necessidade, com
     * outro destinatário e outra base legal, e pede um campo próprio no {@code Aluno} no dia
     * em que existir o que enviar. Misturar as duas coisas num campo só faria a comunicação
     * escolar decidir para onde vai a redefinição de senha, ou o contrário.
     *
     * <p>Nulo é situação normal e prevista — cadastro antigo não tem, e o sistema precisa
     * seguir funcionando sem. O que depende de e-mail simplesmente não acontece.
     */
    private String emailContato;

    private String foto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private boolean ativo = true;

    private Instant criadoEm = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getEmailContato() { return emailContato; }
    public void setEmailContato(String emailContato) { this.emailContato = emailContato; }
    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
}
