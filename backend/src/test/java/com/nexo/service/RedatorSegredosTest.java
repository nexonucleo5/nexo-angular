package com.nexo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** O redator é a barreira entre os logs e as credenciais: cada vazamento conhecido vira um caso. */
class RedatorSegredosTest {

    private static final String SEGREDO_JWT = "segredo-jwt-bem-longo-0123456789-abcdef";
    private static final String SENHA_BANCO = "S3nh@DoBanc0!";

    private final RedatorSegredos redator = new RedatorSegredos(SEGREDO_JWT, SENHA_BANCO);

    @Test
    @DisplayName("a senha que o próprio Spring gerou e registrou")
    void senhaGeradaPeloSpring() {
        String msg = "Using generated security password: 75b0e0cf-559f-42a0-882e-3f6d85bcbd1f\n\nThis generated password is for development use only.";
        String r = redator.mascarar(msg);
        assertThat(r).doesNotContain("75b0e0cf").contains("password: [REDACTED]");
    }

    @Test
    @DisplayName("senha e usuário na URL do banco, de qualquer formato")
    void urlDoBanco() {
        assertThat(redator.mascarar("falha em jdbc:postgresql://nexo:umaSenha123@db.supabase.co:5432/nexo"))
                .doesNotContain("umaSenha123").contains("nexo:[REDACTED]@db.supabase.co");
        assertThat(redator.mascarar("jdbc:postgresql://host/db?user=nexo&password=outraSenha9&ssl=true"))
                .doesNotContain("outraSenha9").contains("password=[REDACTED]&ssl=true");
    }

    @Test
    @DisplayName("os valores reais do segredo do JWT e da senha do banco, onde quer que apareçam")
    void valoresConhecidos() {
        String r = redator.mascarar("erro com chave " + SEGREDO_JWT + " e conexão rejeitada para '" + SENHA_BANCO + "'");
        assertThat(r).doesNotContain(SEGREDO_JWT).doesNotContain(SENHA_BANCO);
    }

    @Test
    @DisplayName("Bearer, JWT solto e hash bcrypt")
    void tokensEHashes() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.assinaturaAbc123_-";
        assertThat(redator.mascarar("Authorization: Bearer " + jwt)).doesNotContain("eyJ");
        assertThat(redator.mascarar("token inválido " + jwt)).doesNotContain("eyJ");
        String hash = "$2a$10$" + "a".repeat(53);
        assertThat(redator.mascarar("usuario senhaHash " + hash + " x")).doesNotContain(hash);
    }

    @Test
    @DisplayName("chave=valor em português e inglês, com aspas ou não")
    void chaveValor() {
        assertThat(redator.mascarar("senha=Abc12345 login=ana")).isEqualTo("senha=[REDACTED] login=ana");
        assertThat(redator.mascarar("{\"password\":\"abc123\",\"x\":1}")).doesNotContain("abc123");
        assertThat(redator.mascarar("spring.datasource.password: segredinho")).doesNotContain("segredinho");
        assertThat(redator.mascarar("refreshToken=xyz987abc")).doesNotContain("xyz987abc");
    }

    @Test
    @DisplayName("e-mail e CPF viram dado parcial")
    void dadoPessoal() {
        assertThat(redator.mascarar("conta maria.silva@escola.com.br")).isEqualTo("conta ***@escola.com.br");
        assertThat(redator.mascarar("cpf 123.456.789-09")).doesNotContain("123.456.789-09");
    }

    @Test
    @DisplayName("texto comum passa intacto, e mascarar duas vezes dá o mesmo resultado")
    void idempotenteENaoDestrutivo() {
        String normal = "Acesso negado (403) em GET /api/admin/contas pelo usuário aluno";
        assertThat(redator.mascarar(normal)).isEqualTo(normal);

        String uma = redator.mascarar("senha=Abc12345 e jdbc:postgresql://u:p4ssw0rd@h/db");
        assertThat(redator.mascarar(uma)).isEqualTo(uma);
        assertThat(redator.mascarar(null)).isNull();
        assertThat(redator.mascarar("")).isEmpty();
    }

    @Test
    @DisplayName("valor curto de configuração não vira mascara em texto comum")
    void literalCurtoIgnorado() {
        var curto = new RedatorSegredos("abc", "");
        assertThat(curto.mascarar("abc def abc")).isEqualTo("abc def abc");
    }
}
