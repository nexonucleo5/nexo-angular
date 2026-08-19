package com.nexo.security;

import com.nexo.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A regra existe porque a validação do formulário Angular não vale para quem chama a API
 * direto — os casos abaixo passariam pelo {@code @Size(min = 8)} do DTO.
 *
 * <p>Cada caso confere o <b>código</b>, e não o texto: é o código que a tela usa para
 * decidir onde mostrar a mensagem, e foi a falta dele que fazia toda recusa de senha nova
 * ser exibida como "senha atual incorreta".
 */
class PoliticaSenhaTest {

    private static final String LOGIN = "mariana.silva@escola.com";
    private static final String NOME = "Mariana Silva";

    private final PoliticaSenha politica = new PoliticaSenha();

    /** Falha o teste se a senha for aceita; devolve a recusa para inspeção. */
    private ApiException recusaDe(String senha) {
        return assertThrows(ApiException.class, () -> politica.validar(senha, LOGIN, NOME));
    }

    @Test
    @DisplayName("senha com variedade suficiente é aceita")
    void senhaBoaPassa() {
        assertThatCode(() -> politica.validar("Chuva#Azul42", LOGIN, NOME)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "\"{0}\" recusada com {1}")
    @CsvSource({
            "12345678,        SENHA_SEQUENCIAL",   // sequência vem antes da lista de dicionário
            "87654321,        SENHA_SEQUENCIAL",   // a mesma sequência ao contrário
            "aaaaaaaa,        SENHA_REPETITIVA",
            "senha123,        SENHA_COMUM",
            "PASSWORD,        SENHA_COMUM",        // a comparação ignora maiúsculas
            "mariana.silva26, SENHA_COM_LOGIN",
            "Mariana@2026,    SENHA_COM_NOME",
    })
    void cadaRecusaTemSeuCodigo(String senha, String codigoEsperado) {
        assertThat(recusaDe(senha).getError()).isEqualTo(codigoEsperado);
    }

    @Test
    @DisplayName("a recusa aponta o campo novaSenha — nunca a senha atual")
    void recusaApontaOCampoCerto() {
        ApiException erro = recusaDe("12345678");

        assertThat(erro.getFields()).containsOnlyKeys("novaSenha");
        assertThat(erro.getFields().get("novaSenha")).isEqualTo(erro.getMessage());
    }

    @Test
    @DisplayName("acento não serve de disfarce: a comparação normaliza")
    void acentoNaoEscapaDaComparacao() {
        assertThat(recusaDe("Máriána#77").getError()).isEqualTo("SENHA_COM_NOME");
    }

    @Test
    @DisplayName("acima de 72 bytes o BCrypt truncaria em silêncio")
    void senhaLongaDemaisRecusada() {
        ApiException erro = recusaDe("Chuva#Azul42".repeat(10)); // 120 caracteres

        assertThat(erro.getError()).isEqualTo("SENHA_LONGA");
        assertThat(erro.getMessage()).contains("72");
    }

    @Test
    @DisplayName("nome curto não vira regra: 'Ana' apareceria em senha boa demais vezes")
    void nomeCurtoNaoBloqueia() {
        assertThatCode(() -> politica.validar("Ananas#Verde9", "ana@escola.com", "Ana Souza"))
                .doesNotThrowAnyException();
    }
}
