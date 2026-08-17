package com.nexo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Endereço residencial do aluno, preenchido pela secretaria a partir do CEP.
 *
 * <p>As colunas levam o prefixo {@code endereco_} de propósito. Um cadastro antigo
 * tinha as colunas {@code alunos.endereco} e {@code alunos.complemento} como texto
 * livre e NOT NULL; elas foram descontinuadas e o {@code SchemaMigracao} as remove
 * a cada arranque. Sem o prefixo, as colunas novas cairiam exatamente nesses nomes
 * e seriam apagadas junto — ou herdariam o NOT NULL antigo e quebrariam todo
 * cadastro novo.
 *
 * <p>Tudo é opcional: a escola matricula antes de ter a documentação completa, e é
 * a própria pendência de documentos que a secretaria acompanha na fila de trabalho.
 */
@Embeddable
public class Endereco {

    /** Só dígitos, 8 posições — a formatação é do cliente. */
    @Column(name = "endereco_cep", length = 8)
    private String cep;

    @Column(name = "endereco_logradouro")
    private String logradouro;

    @Column(name = "endereco_numero", length = 20)
    private String numero;

    @Column(name = "endereco_complemento")
    private String complemento;

    @Column(name = "endereco_bairro")
    private String bairro;

    @Column(name = "endereco_cidade")
    private String cidade;

    @Column(name = "endereco_uf", length = 2)
    private String uf;

    /** Vazio de verdade: distingue "sem endereço" de "endereço em branco". */
    public boolean estaVazio() {
        return vazio(cep) && vazio(logradouro) && vazio(numero)
                && vazio(bairro) && vazio(cidade) && vazio(uf);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    /** Uma linha para exibição — pula o que não foi informado. */
    public String resumo() {
        if (estaVazio()) return null;
        StringBuilder sb = new StringBuilder();
        if (!vazio(logradouro)) sb.append(logradouro);
        if (!vazio(numero)) sb.append(sb.isEmpty() ? "" : ", ").append(numero);
        if (!vazio(bairro)) sb.append(sb.isEmpty() ? "" : " — ").append(bairro);
        if (!vazio(cidade)) {
            sb.append(sb.isEmpty() ? "" : ", ").append(cidade);
            if (!vazio(uf)) sb.append('/').append(uf);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
}
