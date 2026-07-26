package com.nexo.config;

import com.nexo.domain.Materia;
import com.nexo.repository.MateriaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matérias da escola. Diferente do DataSeeder (dados de exemplo, só em banco
 * vazio), este é dado de referência: precisa existir em qualquer ambiente,
 * senão o cadastro de professor não teria o que oferecer.
 */
@Configuration
public class CatalogoMaterias {

    static final List<String> MATERIAS = List.of("Artes", "Biologia", "Educação Física", "Física",
            "Geografia", "História", "Inglês", "Matemática", "Português", "Química");

    @Bean
    @Order(1) // depois da SchemaMigracao, antes do DataSeeder
    CommandLineRunner semearMaterias(MateriaRepository materias) {
        return args -> {
            Set<String> existentes = materias.findAll().stream()
                    .map(Materia::getNome).collect(Collectors.toSet());
            MATERIAS.stream()
                    .filter(nome -> !existentes.contains(nome))
                    .forEach(nome -> materias.save(new Materia(nome)));
        };
    }
}
