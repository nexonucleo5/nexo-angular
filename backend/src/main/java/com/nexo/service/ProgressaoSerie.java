package com.nexo.service;

import java.util.List;
import java.util.Optional;

/**
 * Para onde o aluno vai no ano seguinte.
 *
 * <p>As turmas se chamam "9º Ano B", "1º Ano EM A": série + letra. A promoção
 * mantém a letra e avança a série na ordem do ensino básico. Quem está no 3º ano
 * do médio não tem próxima série — conclui, e isso não é erro, é o fim natural do
 * percurso, tratado como {@link Optional#empty()}.
 */
final class ProgressaoSerie {

    private ProgressaoSerie() {}

    /** Ordem pedagógica; é a mesma do CatalogoTurmas e do ORDEM_ANOS do TurmasController. */
    private static final List<String> SERIES = List.of(
            "6º Ano", "7º Ano", "8º Ano", "9º Ano", "1º Ano EM", "2º Ano EM", "3º Ano EM");

    /**
     * Nome da turma do ano seguinte, mantendo a letra ("9º Ano B" → "1º Ano EM B").
     * Vazio quando é a última série (concluinte) ou quando o nome não segue o padrão
     * — melhor recusar a promoção do que adivinhar destino.
     */
    static Optional<String> proximaTurma(String nomeTurma) {
        if (nomeTurma == null || nomeTurma.isBlank()) return Optional.empty();

        String nome = nomeTurma.trim();
        for (int i = 0; i < SERIES.size(); i++) {
            String serie = SERIES.get(i);
            if (!nome.startsWith(serie)) continue;

            // Última série: conclui, não promove.
            if (i == SERIES.size() - 1) return Optional.empty();

            String letra = nome.substring(serie.length()).trim();
            String proxima = SERIES.get(i + 1);
            return Optional.of(letra.isEmpty() ? proxima : proxima + " " + letra);
        }
        return Optional.empty();
    }

    /** É a última série do ensino básico? Serve para explicar por que não renovou. */
    static boolean ehConcluinte(String nomeTurma) {
        return nomeTurma != null && nomeTurma.trim().startsWith(SERIES.get(SERIES.size() - 1));
    }
}
