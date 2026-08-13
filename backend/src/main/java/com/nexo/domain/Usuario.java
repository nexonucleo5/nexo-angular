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
     * Endereço que recebe de fato mensagem do sistema — recuperação de senha, credencial
     * de primeiro acesso, avisos.
     *
     * <p>Não confundir com o {@code login}, que para aluno e professor é o "e-mail
     * institucional" gerado pelo {@code CredenciaisService} num domínio de fachada
     * ({@code nexo.escola.com}): aquilo é identificador de acesso, não caixa de correio —
     * mandar mensagem para lá não chega a lugar nenhum.
     *
     * <p>Para aluno menor de idade, o endereço aqui é o do responsável. É por isso que o
     * campo mora no {@code Usuario} e não no {@code Aluno}: quem recebe a mensagem nem
     * sempre é a pessoa que estuda.
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
