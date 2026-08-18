package com.nexo;

import com.nexo.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a correção do achado da revisão de segurança: o seed criava a conta
 * administrativa com a senha padrão em QUALQUER banco já povoado, incluindo o de
 * produção — porque nem o Dockerfile nem o compose.yaml ativam o perfil prod, e
 * fora dele nexo.seed.enabled continua true.
 *
 * <p>Aqui o banco é H2 (o de desenvolvimento), então a conta existe. O que a
 * correção garante é o outro lado: fora do H2, o seed não escreve — ver
 * DataSeeder.bancoDeDesenvolvimento(), que é a única porta para essa criação.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-seed-guard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class SeedNaoVazaParaProducaoTest extends TesteApiBase {

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    @DisplayName("no banco de desenvolvimento (H2) as contas de exemplo existem")
    void contasDeExemploNoH2() {
        assertThat(usuarios.existsByLoginIgnoreCase("admin")).isTrue();
        assertThat(usuarios.existsByLoginIgnoreCase("diretor")).isTrue();
    }

    @Test
    @DisplayName("a criação da conta de exemplo é decidida pela URL do banco, não pela flag")
    void guardaOlhaAUrlDoBanco() throws Exception {
        // Reflexão de propósito: o método é interno ao seeder e o que precisa ser
        // travado é justamente a decisão dele — que um jdbc:postgresql não passa.
        var seeder = Class.forName("com.nexo.config.DataSeeder$Seeder");
        var metodo = seeder.getDeclaredMethod("bancoDeDesenvolvimento");
        metodo.setAccessible(true);

        assertThat(chamarCom(seeder, metodo, "jdbc:h2:file:./data/nexo")).isTrue();
        assertThat(chamarCom(seeder, metodo, "jdbc:h2:mem:teste")).isTrue();
        assertThat(chamarCom(seeder, metodo, "jdbc:postgresql://host:5432/nexo"))
                .as("banco de produção não pode receber conta com senha padrão").isFalse();
        assertThat(chamarCom(seeder, metodo, "")).isFalse();
        assertThat(chamarCom(seeder, metodo, null)).isFalse();
    }

    /** Instancia o record do seeder só com a URL preenchida — é o único campo que a guarda lê. */
    private boolean chamarCom(Class<?> seeder, java.lang.reflect.Method metodo, String url) throws Exception {
        var construtor = seeder.getDeclaredConstructors()[0];
        construtor.setAccessible(true);
        Object[] args = new Object[construtor.getParameterCount()];
        args[args.length - 1] = url; // jdbcUrl é o último componente do record
        return (boolean) metodo.invoke(construtor.newInstance(args));
    }
}
