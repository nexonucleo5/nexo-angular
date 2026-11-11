package com.nexo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chat: anexos (até 3, áudio incluso), download restrito aos participantes e apagar mensagem. */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-chat-anexos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class ChatAnexosTest extends TesteApiBase {

    private long idDe(String login) throws Exception {
        return autenticar(login, SENHA_PADRAO).get("usuario").get("id").asLong();
    }

    private static MockMultipartFile arquivo(String nome, String tipo) {
        return new MockMultipartFile("arquivos", nome, tipo, ("conteudo de " + nome).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode enviar(String login, long para, String texto, MockMultipartFile... arquivos) throws Exception {
        var req = multipart("/api/chat/" + para + "/mensagens");
        for (var a : arquivos) req.file(a);
        if (texto != null) req.param("texto", texto);
        req.header(HttpHeaders.AUTHORIZATION, bearer(login));
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("mensagem com 3 anexos (um deles áudio) aparece no histórico e o destinatário baixa")
    void enviaEListaAnexos() throws Exception {
        long diretor = idDe("diretor");
        JsonNode msg = enviar("professor", diretor, "segue",
                arquivo("a.pdf", "application/pdf"),
                arquivo("b.png", "image/png"),
                arquivo("audio.webm", "audio/webm;codecs=opus"));

        assertThat(msg.get("anexos")).hasSize(3);
        assertThat(msg.get("anexos").get(2).get("tipo").asText()).isEqualTo("audio/webm");

        long professor = idDe("professor");
        JsonNode historico = json.readTree(mvc.perform(get("/api/chat/" + professor)
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode ultima = historico.get(historico.size() - 1);
        assertThat(ultima.get("id").asLong()).isEqualTo(msg.get("id").asLong());
        assertThat(ultima.get("anexos")).hasSize(3);

        String anexoId = msg.get("anexos").get(0).get("id").asText();
        mvc.perform(get("/api/chat/anexos/" + anexoId).header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("mais de 3 arquivos é recusado")
    void limiteDeTresArquivos() throws Exception {
        long diretor = idDe("diretor");
        mvc.perform(multipart("/api/chat/" + diretor + "/mensagens")
                        .file(arquivo("1.txt", "text/plain")).file(arquivo("2.txt", "text/plain"))
                        .file(arquivo("3.txt", "text/plain")).file(arquivo("4.txt", "text/plain"))
                        .header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quem não participa da conversa não baixa o anexo")
    void anexoSoParaParticipantes() throws Exception {
        long diretor = idDe("diretor");
        JsonNode msg = enviar("professor", diretor, null, arquivo("x.txt", "text/plain"));
        String anexoId = msg.get("anexos").get(0).get("id").asText();

        mvc.perform(get("/api/chat/anexos/" + anexoId).header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("apagar para mim: some só do histórico de quem apagou, mesmo sendo mensagem recebida")
    void apagarParaMim() throws Exception {
        long diretor = idDe("diretor");
        long professor = idDe("professor");
        JsonNode msg = enviar("professor", diretor, "só o diretor vai esconder", arquivo("y.txt", "text/plain"));
        long id = msg.get("id").asLong();

        // O diretor recebeu a mensagem e apaga para si.
        mvc.perform(post("/api/chat/mensagens/" + id + "/ocultar")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isNoContent());
        // Repetir não dá erro.
        mvc.perform(post("/api/chat/mensagens/" + id + "/ocultar")
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isNoContent());

        assertThat(idsDoHistorico("diretor", professor)).doesNotContain(id);
        assertThat(idsDoHistorico("professor", diretor)).contains(id);

        String anexoId = msg.get("anexos").get(0).get("id").asText();
        mvc.perform(get("/api/chat/anexos/" + anexoId).header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/anexos/" + anexoId).header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isOk());

        // Quem não participa da conversa não consegue ocultar.
        mvc.perform(post("/api/chat/mensagens/" + id + "/ocultar")
                        .header(HttpHeaders.AUTHORIZATION, bearer("aluno")))
                .andExpect(status().isNotFound());

        // O autor ainda pode apagar para todos depois disso.
        mvc.perform(delete("/api/chat/mensagens/" + id).header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isNoContent());
    }

    private java.util.List<Long> idsDoHistorico(String login, long outroId) throws Exception {
        String corpo = mvc.perform(get("/api/chat/" + outroId).header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var ids = new java.util.ArrayList<Long>();
        for (JsonNode m : json.readTree(corpo)) ids.add(m.get("id").asLong());
        return ids;
    }

    @Test
    @DisplayName("só o autor apaga, e a mensagem some do histórico dos dois")
    void apagarMensagem() throws Exception {
        long diretor = idDe("diretor");
        long professor = idDe("professor");
        JsonNode msg = enviar("professor", diretor, "vou apagar", arquivo("x.txt", "text/plain"));
        long id = msg.get("id").asLong();

        mvc.perform(delete("/api/chat/mensagens/" + id).header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/chat/mensagens/" + id).header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isNoContent());

        String historico = mvc.perform(get("/api/chat/" + professor)
                        .header(HttpHeaders.AUTHORIZATION, bearer("diretor")))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode m : json.readTree(historico)) {
            assertThat(m.get("id").asLong()).isNotEqualTo(id);
        }
        String anexoId = msg.get("anexos").get(0).get("id").asText();
        mvc.perform(get("/api/chat/anexos/" + anexoId).header(HttpHeaders.AUTHORIZATION, bearer("professor")))
                .andExpect(status().isNotFound());
    }
}
