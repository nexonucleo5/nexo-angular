package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envio e entrega da foto de perfil. O ponto central é que o formato sai do
 * conteúdo do arquivo, não do Content-Type que o cliente declarou: os bytes
 * voltam depois em /api/fotos/{id}, público e na mesma origem da aplicação.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-foto;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class FotoPerfilTest extends TesteApiBase {

    private static byte[] pngValido() {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(new byte[64]);
        return out.toByteArray();
    }

    @Test
    @DisplayName("arquivo que não é imagem é recusado mesmo declarando image/png")
    void conteudoFalsificado() throws Exception {
        var html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        mvc.perform(multipart("/api/usuarios/me/foto")
                        .file(new MockMultipartFile("arquivo", "foto.png", "image/png", html))
                        .header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PNG real é aceito mesmo declarado como octet-stream")
    void tipoDetectadoPeloConteudo() throws Exception {
        mvc.perform(multipart("/api/usuarios/me/foto")
                        .file(new MockMultipartFile("arquivo", "x.bin",
                                "application/octet-stream", pngValido()))
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a foto é servida com o tipo detectado e cache longo, sem exigir token")
    void entregaDaFoto() throws Exception {
        JsonNode usuario = json.readTree(mvc.perform(multipart("/api/usuarios/me/foto")
                        .file(new MockMultipartFile("arquivo", "x.png", "image/png", pngValido()))
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String url = usuario.get("foto").asText();
        assertThat(url).startsWith("/api/fotos/");

        // Sem Authorization de propósito: <img src> não envia o cabeçalho.
        var resposta = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse();

        assertThat(resposta.getHeader(HttpHeaders.CACHE_CONTROL))
                .as("o id é um UUID que muda a cada envio, então a imagem pode ser cacheada")
                .contains("immutable");
    }

    @Test
    @DisplayName("id de foto inexistente devolve 404")
    void fotoInexistente() throws Exception {
        mvc.perform(get("/api/fotos/nao-existe")).andExpect(status().isNotFound());
    }
}
