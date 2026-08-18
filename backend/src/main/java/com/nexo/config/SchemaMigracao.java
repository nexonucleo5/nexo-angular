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
 * Ajusta bancos criados por versões anteriores. O Hibernate roda com
 * ddl-auto=update, que cria e altera mas nunca remove — sem isto os dados pessoais
 * que o sistema deixou de coletar continuariam gravados nas colunas antigas,
 * invisíveis para a aplicação e presentes em qualquer dump.
 *
 * Idempotente e válido em H2 (modo PostgreSQL) e Postgres.
 */
@Configuration
public class SchemaMigracao {

    /**
     * Colunas e tabelas de dado pessoal e de vida escolar que este sistema não
     * guarda mais. O DROP é o ponto da mudança: enquanto a coluna existe o dado
     * continua ao alcance de quem chegar ao banco, por mais que nenhuma tela o mostre.
     *
     * <p>{@code alunos.endereco} e {@code alunos.complemento} são do cadastro em texto
     * livre, anterior ao endereço estruturado; as {@code endereco_*} são o estruturado
     * que veio depois. Os dois saem.
     */
    private static final List<String> ESTRUTURAS_REMOVIDAS = List.of(
            // Dados pessoais do aluno
            "alter table if exists alunos drop column if exists cpf",
            "alter table if exists alunos drop column if exists telefone",
            "alter table if exists alunos drop column if exists email_responsavel",
            "alter table if exists alunos drop column if exists cpf_responsavel",
            "alter table if exists alunos drop column if exists telefone_responsavel",
            "alter table if exists alunos drop column if exists sexo",
            "alter table if exists alunos drop column if exists data_nascimento",
            "alter table if exists alunos drop column if exists endereco",
            "alter table if exists alunos drop column if exists complemento",
            "alter table if exists alunos drop column if exists endereco_cep",
            "alter table if exists alunos drop column if exists endereco_logradouro",
            "alter table if exists alunos drop column if exists endereco_numero",
            "alter table if exists alunos drop column if exists endereco_complemento",
            "alter table if exists alunos drop column if exists endereco_bairro",
            "alter table if exists alunos drop column if exists endereco_cidade",
            "alter table if exists alunos drop column if exists endereco_uf",
            // Checklist de documentos (RG, CPF, comprovante de residência, ...)
            "drop table if exists documentos_entregues",
            // Vida escolar: o vínculo virou inscricoes, sem status nem documentação
            "alter table if exists inscricoes drop column if exists status",
            "alter table if exists inscricoes drop column if exists documentacao",
            "alter table if exists inscricoes drop column if exists ano_letivo",
            "alter table if exists inscricoes drop column if exists origem_matricula_id",
            "alter table if exists inscricoes drop column if exists data_matricula",
            "alter table if exists professores drop column if exists disciplina");

    @Bean
    @Order(0) // antes de qualquer runner que grave dados
    CommandLineRunner migrarSchema(JdbcTemplate jdbc, MateriaRepository materias) {
        return args -> {
            migrarDisciplinaParaMaterias(jdbc, materias);
            renomearMatriculasParaInscricoes(jdbc);
            ESTRUTURAS_REMOVIDAS.forEach(jdbc::execute);
            renomearSecretariaParaAdmin(jdbc);
            liberarPapeisNovos(jdbc);
            // O ddl-auto=update cria turmas.capacidade sem valor: linhas antigas
            // ficam NULL. O getter cobre a leitura, mas o banco fica consistente aqui.
            jdbc.execute("update turmas set capacidade = " + com.nexo.domain.Turma.CAPACIDADE_PADRAO
                    + " where capacidade is null");
        };
    }

    /**
     * Renomeia {@code matriculas} para {@code inscricoes}, preservando o vínculo
     * aluno-turma que já estava lá. Sem isto o ddl-auto=update criaria a tabela nova
     * vazia e deixaria a antiga de lado: todo aluno perderia a turma de estudo, e a
     * tabela com status e documentação continuaria inteira no banco.
     *
     * <p>O que era CANCELADA ou TRANCADA vira {@code ativo = false}: as duas diziam
     * "este aluno não está cursando", que é o que a coluna nova guarda.
     */
    private void renomearMatriculasParaInscricoes(JdbcTemplate jdbc) {
        if (!tabelaExiste(jdbc, "matriculas") || tabelaExiste(jdbc, "inscricoes")) return;

        jdbc.execute("alter table matriculas rename to inscricoes");

        // As colunas novas nascem aqui, e não pelo Hibernate: ele não consegue
        // adicionar coluna NOT NULL a uma tabela que já tem linhas.
        if (!colunaExiste(jdbc, "inscricoes", "ativo")) {
            jdbc.execute("alter table inscricoes add column ativo boolean");
        }
        if (colunaExiste(jdbc, "inscricoes", "status")) {
            jdbc.execute("update inscricoes set ativo = (status not in ('CANCELADA','TRANCADA'))");
        }
        jdbc.execute("update inscricoes set ativo = true where ativo is null");

        if (!colunaExiste(jdbc, "inscricoes", "criada_em")) {
            jdbc.execute("alter table inscricoes add column criada_em timestamp");
        }
        if (colunaExiste(jdbc, "inscricoes", "data_matricula")) {
            jdbc.execute("update inscricoes set criada_em = cast(data_matricula as timestamp) "
                    + "where criada_em is null and data_matricula is not null");
        }
    }

    /**
     * Converte as contas do antigo papel SECRETARIA para ADMIN. Solta o CHECK dos
     * papéis antes do UPDATE: enquanto ele estiver de pé listando só os papéis
     * antigos, o Postgres recusa a gravação de 'ADMIN'.
     */
    private void renomearSecretariaParaAdmin(JdbcTemplate jdbc) {
        liberarPapeisNovos(jdbc);
        jdbc.update("update usuarios set cargo = 'Administração do sistema' "
                + "where role = 'SECRETARIA' and cargo = 'Secretaria Escolar'");
        jdbc.update("update usuarios set role = 'ADMIN' where role = 'SECRETARIA'");
    }

    /**
     * Libera papéis criados depois da tabela usuarios em usuarios.role.
     *
     * <p>O Hibernate congela a lista de papéis quando <b>cria</b> a tabela, e
     * ddl-auto=update mexe em coluna mas nunca desfaz isso. Num banco criado antes de
     * {@link com.nexo.domain.Role#ADMIN} gravar o papel novo estoura ("Value not
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
        for (Map<String, Object> check : checksDePapel(jdbc)) {
            jdbc.execute("alter table usuarios drop constraint if exists " + aspas(check.get("nome")));
        }
    }

    /**
     * Os CHECK de usuarios que listam papéis. O filtro por PROFESSOR separa o check
     * dos papéis dos NOT NULL, que o Postgres também expõe como CHECK e não podem
     * ser removidos.
     */
    private List<Map<String, Object>> checksDePapel(JdbcTemplate jdbc) {
        List<Map<String, Object>> checks = jdbc.queryForList("""
                select tc.constraint_name as nome, cc.check_clause as clausula
                from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_name = tc.constraint_name
                 and cc.constraint_schema = tc.constraint_schema
                where lower(tc.table_name) = 'usuarios' and tc.constraint_type = 'CHECK'
                """);
        return checks.stream()
                .filter(c -> String.valueOf(c.get("clausula")).toUpperCase().contains("PROFESSOR"))
                .toList();
    }

    /** Nome de objeto entre aspas duplas, como o identificador que ele é. */
    private static String aspas(Object identificador) {
        return '"' + String.valueOf(identificador) + '"';
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

    private boolean tabelaExiste(JdbcTemplate jdbc, String tabela) {
        Integer total = jdbc.queryForObject("""
                select count(*) from information_schema.tables where lower(table_name) = ?
                """, Integer.class, tabela);
        return total != null && total > 0;
    }

    private boolean temVinculo(JdbcTemplate jdbc, Long professorId) {
        Integer total = jdbc.queryForObject(
                "select count(*) from professor_materias where professor_id = ?", Integer.class, professorId);
        return total != null && total > 0;
    }
}
