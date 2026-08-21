package com.nexo.domain;

/**
 * Papéis do sistema.
 *
 * <p>{@code ADMIN} substituiu o antigo {@code SECRETARIA}. A troca não é cosmética:
 * a secretaria era um papel de escola — matrícula, documento, atendimento a
 * responsável. Este sistema não faz nada disso (a escola tem outro sistema para a
 * vida escolar), então o papel administrativo aqui cuida de contas e do catálogo
 * de conteúdo. Bancos gravados com SECRETARIA são convertidos no arranque, em
 * {@link com.nexo.config.SchemaMigracao}.
 */
public enum Role {
    ALUNO, PROFESSOR, DIRETOR, ADMIN
}
