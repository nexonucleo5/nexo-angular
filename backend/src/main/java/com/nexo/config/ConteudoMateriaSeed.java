package com.nexo.config;

import com.nexo.domain.ConteudoMateria;
import com.nexo.domain.Materia;
import com.nexo.repository.ConteudoMateriaRepository;
import com.nexo.repository.MateriaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Prova de conceito do novo modelo de conteúdo: popula apenas Matemática com
 * alguns tópicos de exemplo. As demais matérias ficam sem conteúdo por ora —
 * a tela de disciplina trata esse caso caindo de volta nos tópicos estáticos.
 * Roda sempre (não só em banco vazio) para o piloto aparecer em qualquer ambiente.
 */
@Configuration
public class ConteudoMateriaSeed {

    @Bean
    @Order(4) // depois do CatalogoMaterias (garante que "Matemática" já existe)
    CommandLineRunner semearConteudoMatematica(MateriaRepository materias, ConteudoMateriaRepository conteudos) {
        return args -> {
            Materia matematica = materias.findAllByOrderByNome().stream()
                    .filter(m -> m.getNome().equalsIgnoreCase("Matemática"))
                    .findFirst()
                    .orElse(null);
            if (matematica == null || conteudos.existsByMateriaId(matematica.getId())) return;

            conteudo(conteudos, matematica, "Funções do 1º Grau", 1, """
                    Uma função do 1º grau tem a forma f(x) = ax + b, com a diferente de zero. \
                    O coeficiente 'a' define a inclinação da reta (se positivo, a função é crescente; \
                    se negativo, decrescente) e 'b' é o valor de f(0), onde a reta cruza o eixo y. \
                    Para encontrar a raiz da função (onde f(x) = 0), basta resolver ax + b = 0, \
                    isolando x: x = -b/a.""");

            conteudo(conteudos, matematica, "Teorema de Pitágoras", 2, """
                    Em todo triângulo retângulo, o quadrado da medida da hipotenusa é igual à soma \
                    dos quadrados das medidas dos catetos: a² = b² + c². A hipotenusa é sempre o lado \
                    oposto ao ângulo reto (90°) e o maior lado do triângulo. Esse teorema é usado para \
                    calcular distâncias, alturas e é a base da trigonometria no triângulo retângulo.""");

            conteudo(conteudos, matematica, "Introdução à Probabilidade", 3, """
                    A probabilidade mede a chance de um evento acontecer, calculada pela razão entre \
                    o número de casos favoráveis e o número total de casos possíveis: P(evento) = \
                    casos favoráveis / casos possíveis. O resultado varia entre 0 (evento impossível) \
                    e 1 (evento certo) e pode ser expresso também em porcentagem — por exemplo, a \
                    chance de tirar um número par em um dado de 6 faces é 3/6 = 0,5, ou 50%.""");
        };
    }

    private void conteudo(ConteudoMateriaRepository conteudos, Materia materia, String titulo, int ordem, String texto) {
        ConteudoMateria c = new ConteudoMateria();
        c.setMateria(materia);
        c.setTitulo(titulo);
        c.setOrdem(ordem);
        c.setTexto(texto);
        conteudos.save(c);
    }
}
