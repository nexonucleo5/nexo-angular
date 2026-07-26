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
            "alter table if exists alunos drop column if exists endereco",
            "alter table if exists alunos drop column if exists complemento",
            "alter table if exists professores drop column if exists disciplina");

    @Bean
    @Order(0) // antes de qualquer runner que grave dados
    CommandLineRunner migrarSchema(JdbcTemplate jdbc, MateriaRepository materias) {
        return args -> {
            migrarDisciplinaParaMaterias(jdbc, materias);
            COLUNAS_REMOVIDAS.forEach(jdbc::execute);
        };
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
