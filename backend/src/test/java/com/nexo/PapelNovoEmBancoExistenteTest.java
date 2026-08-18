package com.nexo;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a correção do papel novo em banco que já existia.
 *
 * <p>A tabela usuarios nasce com a lista de papéis congelada pelo Hibernate — ENUM
 * nativo no H2, CHECK no Postgres — e ddl-auto=update nunca desfaz isso. Num banco
 * criado antes de {@link Role#ADMIN}, gravar o papel novo estourava "Value not
 * permitted for column" e derrubava a aplicação no arranque, já que quem grava é o
 * seeder. Quem descongela é o SchemaMigracao — que também é quem converte as contas
 * do antigo papel SECRETARIA.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-papel-novo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class PapelNovoEmBancoExistenteTest extends TesteApiBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    @DisplayName("usuarios.role não guarda a lista de papéis, então um papel novo entra sem migração")
    void colunaDePapelNaoCongelaOsValores() {
        String tipo = jdbc.queryForObject("""
                select data_type from information_schema.columns
                where lower(table_name) = 'usuarios' and lower(column_name) = 'role'
                """, String.class);

        // ENUM nativo traria a lista dentro do próprio tipo — é dele que a migração tira.
        assertThat(tipo).as("o tipo da coluna não pode carregar a lista de papéis")
                .doesNotContainIgnoringCase("enum");

        Usuario nova = new Usuario();
        nova.setLogin("admin-2");
        nova.setSenhaHash("irrelevante-para-este-teste");
        nova.setNome("Segundo Administrador");
        nova.setCargo("Administração do sistema");
        nova.setRole(Role.ADMIN);

        assertThat(usuarios.save(nova).getId()).isNotNull();
    }

    @Test
    @DisplayName("o seed cria a conta do administrador e ela entra com a senha padrão")
    void contaDoAdministradorExiste() throws Exception {
        assertThat(usuarios.existsByLoginIgnoreCase("admin")).isTrue();

        // O mesmo caminho das outras contas: login + senha padrão devolvem sessão.
        var sessao = autenticar("admin", SENHA_PADRAO);
        assertThat(sessao.get("token").asText()).isNotBlank();
        assertThat(sessao.get("usuario").get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("o papel SECRETARIA não existe mais em lugar nenhum do enum")
    void papelAntigoSumiu() {
        assertThat(java.util.Arrays.stream(Role.values()).map(Enum::name))
                .doesNotContain("SECRETARIA")
                .contains("ADMIN");
    }
}
