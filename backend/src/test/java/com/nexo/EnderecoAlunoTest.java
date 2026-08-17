package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endereço do aluno: cadastro com endereço, atualização e a consulta de CEP.
 *
 * <p>Os testes de CEP aqui de propósito não tocam a rede: a validação do formato e a
 * autorização acontecem antes de qualquer chamada externa. Amarrar a suíte à BrasilAPI
 * deixaria o build vermelho por causa de um serviço de terceiro fora do ar.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-endereco;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class EnderecoAlunoTest extends TesteApiBase {

    private long primeiraTurma(String bearer) throws Exception {
        JsonNode turmas = json.readTree(mvc.perform(get("/api/turmas")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn().getResponse().getContentAsString());
        return turmas.get(0).get("id").asLong();
    }

    @Test
    @DisplayName("secretaria cadastra aluno com endereço e ele volta no detalhe")
    void cadastroComEndereco() throws Exception {
        String secretaria = bearer("secretaria");
        long turmaId = primeiraTurma(secretaria);

        String location = mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Aluna Com Endereco","dataNascimento":"2011-05-06","sexo":"F",
                                 "turmaId":%d,
                                 "endereco":{"cep":"01001-000","logradouro":"Praça da Sé","numero":"10",
                                             "complemento":"apto 2","bairro":"Sé","cidade":"São Paulo","uf":"sp"}}
                                """.formatted(turmaId))
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();

        mvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                // O CEP é gravado só com dígitos e a UF em maiúsculo, para casar depois.
                .andExpect(jsonPath("$.endereco.cep").value("01001000"))
                .andExpect(jsonPath("$.endereco.uf").value("SP"))
                .andExpect(jsonPath("$.endereco.numero").value("10"))
                .andExpect(jsonPath("$.endereco.resumo").value("Praça da Sé, 10 — Sé, São Paulo/SP"));
    }

    @Test
    @DisplayName("aluno sem endereço devolve null, não um objeto vazio")
    void semEnderecoVemNulo() throws Exception {
        String secretaria = bearer("secretaria");
        long turmaId = primeiraTurma(secretaria);

        String location = mvc.perform(post("/api/alunos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluno Sem Endereco\",\"dataNascimento\":\"2012-01-02\","
                                + "\"sexo\":\"M\",\"turmaId\":" + turmaId + "}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco").doesNotExist());
    }

    @Test
    @DisplayName("PUT substitui o endereço inteiro e a UF inválida é recusada")
    void atualizarEndereco() throws Exception {
        String secretaria = bearer("secretaria");

        mvc.perform(put("/api/alunos/1/endereco").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cep":"20040-002","logradouro":"Avenida Rio Branco","numero":"1",
                                 "bairro":"Centro","cidade":"Rio de Janeiro","uf":"RJ"}
                                """)
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidade").value("Rio de Janeiro"));

        // PUT é substituição: o complemento que não veio no corpo sai do registro.
        mvc.perform(put("/api/alunos/1/endereco").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cep\":\"20040-002\",\"cidade\":\"Rio de Janeiro\",\"uf\":\"RJ\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complemento").doesNotExist());

        mvc.perform(put("/api/alunos/1/endereco").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uf\":\"Sao Paulo\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/alunos/1/endereco").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cep\":\"123\"}")
                        .header(HttpHeaders.AUTHORIZATION, secretaria))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a consulta de CEP valida o formato e é restrita a quem cadastra")
    void consultaDeCep() throws Exception {
        // CEP malformado nem chega ao provedor externo: 400 antes de qualquer rede.
        for (String invalido : new String[]{"123", "abcdefgh", "00000000"}) {
            mvc.perform(get("/api/cep/" + invalido)
                            .header(HttpHeaders.AUTHORIZATION, bearer("secretaria")))
                    .andExpect(status().isBadRequest());
        }

        // Quem não cadastra aluno não consulta CEP — a rota não é proxy aberto.
        for (String perfil : new String[]{"aluno", "professor"}) {
            mvc.perform(get("/api/cep/01001000")
                            .header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(get("/api/cep/01001000")).andExpect(status().isUnauthorized());
    }
}
