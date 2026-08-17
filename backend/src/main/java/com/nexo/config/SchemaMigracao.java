package com.nexo.config;

import com.nexo.domain.Materia;
import com.nexo.repository.MateriaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Ajusta bancos criados antes do novo cadastro. O Hibernate roda com
 * ddl-auto=update, que cria e altera mas nunca remove — sem isto a coluna
 * alunos.cpf continuaria NOT NULL e todo cadastro novo falharia.
 *
 * Idempotente e válido em H2 (modo PostgreSQL) e Postgres.
 */
@Configuration
public class SchemaMigracao {

    private static final List<String> COLUNAS_REMOVIDAS = List.of(
            "alter table if exists alunos drop column if exists cpf",
            "alter table if exists alunos drop column if exists telefone",
            "alter table if exists alunos drop column if exists email_responsavel",
            "alter table if exists alunos drop column if exists cpf_responsavel",
            "alter table if exists alunos drop column if exists telefone_responsavel",
            // Estas duas são as colunas de TEXTO LIVRE do cadastro antigo, e continuam
            // saindo: em banco velho vinham NOT NULL e quebrariam todo cadastro novo.
            // O endereço atual não conflita com elas de propósito — as colunas do
            // Endereco levam o prefixo endereco_ (endereco_logradouro,
            // endereco_complemento, ...) justamente para não cair nesses nomes e ser
            // apagado aqui a cada arranque. Ver com.nexo.domain.Endereco.
            "alter table if exists alunos drop column if exists endereco",
            "alter table if exists alunos drop column if exists complemento",
            "alter table if exists professores drop column if exists disciplina");

    @Bean
    @Order(0) // antes de qualquer runner que grave dados
    CommandLineRunner migrarSchema(JdbcTemplate jdbc, MateriaRepository materias) {
        return args -> {
            migrarDisciplinaParaMaterias(jdbc, materias);
            COLUNAS_REMOVIDAS.forEach(jdbc::execute);
            liberarPapeisNovos(jdbc);
            // O ddl-auto=update cria turmas.capacidade sem valor: linhas antigas
            // ficam NULL. O getter cobre a leitura, mas o banco fica consistente aqui.
            jdbc.execute("update turmas set capacidade = " + com.nexo.domain.Turma.CAPACIDADE_PADRAO
                    + " where capacidade is null");
        };
    }

    /**
     * Libera papéis criados depois da tabela usuarios em usuarios.role.
     *
     * <p>O Hibernate congela a lista de papéis quando <b>cria</b> a tabela, e
     * ddl-auto=update mexe em coluna mas nunca desfaz isso. Num banco criado antes de
     * {@link com.nexo.domain.Role#SECRETARIA} gravar o papel novo estoura ("Value not
     * permitted for column") e derruba a aplicação no arranque, porque quem grava é o
     * seeder. O congelamento tem forma diferente em cada banco:
     *
     * <ul>
     *   <li><b>H2</b> (desenvolvimento): a coluna é do tipo ENUM nativo, com os valores
     *       no próprio tipo — vira {@code varchar};</li>
     *   <li><b>Postgres</b> (deploy): a coluna é varchar com um CHECK listando os
     *       valores — o check sai.</li>
     * </ul>
     *
     * <p>Cada passo é detectado no catálogo e não faz nada onde não se aplica, então os
     * dois convivem no mesmo método. Depois disto quem valida o papel é o enum no Java
     * ({@code @Enumerated(EnumType.STRING)} e a checagem do JwtAuthFilter), e um papel
     * futuro não precisa de migração nenhuma.
     */
    private void liberarPapeisNovos(JdbcTemplate jdbc) {
        if (roleEhEnumNativo(jdbc)) {
            jdbc.execute("alter table usuarios alter column role set data type varchar(30)");
        }

        List<Map<String, Object>> checks = jdbc.queryForList("""
                select tc.constraint_name as nome, cc.check_clause as clausula
                from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_name = tc.constraint_name
                 and cc.constraint_schema = tc.constraint_schema
                where lower(tc.table_name) = 'usuarios' and tc.constraint_type = 'CHECK'
                """);

        for (Map<String, Object> check : checks) {
            String clausula = String.valueOf(check.get("clausula")).toUpperCase();
            // Só o check dos papéis: o que já conhece SECRETARIA está em dia, e os
            // NOT NULL que o Postgres também expõe como CHECK não podem ser removidos.
            if (!clausula.contains("PROFESSOR") || clausula.contains("SECRETARIA")) continue;
            jdbc.execute("alter table usuarios drop constraint if exists \"" + check.get("nome") + "\"");
        }
    }

    private boolean roleEhEnumNativo(JdbcTemplate jdbc) {
        Integer total = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = 'usuarios' and lower(column_name) = 'role'
                  and upper(data_type) = 'ENUM'
                """, Integer.class);
        return total != null && total > 0;
    }

    /**
     * Converte o antigo texto professores.disciplina em vínculos professor_materias,
     * criando as matérias que ainda não existirem. Roda antes do DROP da coluna.
     */
    private void migrarDisciplinaParaMaterias(JdbcTemplate jdbc, MateriaRepository materias) {
        if (!colunaExiste(jdbc, "professores", "disciplina")) return;

        List<Map<String, Object>> docentes = jdbc.queryForList(
                "select id, disciplina from professores where disciplina is not null and disciplina <> ''");

        for (Map<String, Object> docente : docentes) {
            Long professorId = ((Number) docente.get("id")).longValue();
            if (temVinculo(jdbc, professorId)) continue;

            for (String nome : Arrays.stream(String.valueOf(docente.get("disciplina")).split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList()) {
                Materia materia = materias.findAllByOrderByNome().stream()
                        .filter(m -> m.getNome().equalsIgnoreCase(nome))
                        .findFirst()
                        .orElseGet(() -> materias.save(new Materia(nome)));
                jdbc.update("insert into professor_materias (professor_id, materia_id) values (?, ?)",
                        professorId, materia.getId());
            }
        }
    }

    private boolean colunaExiste(JdbcTemplate jdbc, String tabela, String coluna) {
        Integer total = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = ? and lower(column_name) = ?
                """, Integer.class, tabela, coluna);
        return total != null && total > 0;
    }

    private boolean temVinculo(JdbcTemplate jdbc, Long professorId) {
        Integer total = jdbc.queryForObject(
                "select count(*) from professor_materias where professor_id = ?", Integer.class, professorId);
        return total != null && total > 0;
    }
}
