package com.nexo.config;

import com.nexo.domain.Desafio;
import com.nexo.domain.QuizPergunta;
import com.nexo.repository.DesafioRepository;
import com.nexo.repository.QuizPerguntaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perguntas de quiz de exemplo para os desafios já existentes no catálogo
 * (chave: título do desafio, igual ao semeado em DataSeeder). Roda sempre —
 * não só em banco vazio como o DataSeeder — e não duplica: só insere
 * perguntas para desafios que ainda não têm nenhuma.
 */
@Configuration
public class QuizPerguntasSeed {

    private record Pergunta(String enunciado, List<String> alternativas, int respostaCorreta) {}

    private static final Map<String, List<Pergunta>> PERGUNTAS_POR_DESAFIO = new LinkedHashMap<>();
    static {
        PERGUNTAS_POR_DESAFIO.put("Quiz: Fotossíntese", List.of(
                new Pergunta("Qual é o principal pigmento responsável por captar luz na fotossíntese?",
                        List.of("Clorofila", "Melanina", "Queratina", "Hemoglobina"), 0),
                new Pergunta("Em qual estrutura da célula vegetal ocorre a fotossíntese?",
                        List.of("Mitocôndria", "Cloroplasto", "Núcleo", "Ribossomo"), 1),
                new Pergunta("Quais são os principais produtos da fotossíntese?",
                        List.of("Água e CO2", "Glicose e Oxigênio", "Nitrogênio e Água", "ATP e CO2"), 1)));

        PERGUNTAS_POR_DESAFIO.put("Funções Trigonométricas", List.of(
                new Pergunta("Qual é o valor de sen(90°)?", List.of("0", "1", "-1", "0,5"), 1),
                new Pergunta("Qual função trigonométrica relaciona cateto adjacente e hipotenusa?",
                        List.of("Seno", "Cosseno", "Tangente", "Cotangente"), 1),
                new Pergunta("O período da função seno é:", List.of("90°", "180°", "360°", "270°"), 2)));

        PERGUNTAS_POR_DESAFIO.put("Present Perfect", List.of(
                new Pergunta("Qual estrutura representa o Present Perfect?",
                        List.of("have/has + particípio passado", "will + verbo", "be + verbo-ing", "did + verbo"), 0),
                new Pergunta("Complete: \"She ___ already finished her homework.\"",
                        List.of("have", "has", "had", "having"), 1),
                new Pergunta("Qual frase está no Present Perfect?",
                        List.of("I am eating", "I have eaten", "I ate", "I will eat"), 1)));

        PERGUNTAS_POR_DESAFIO.put("Revolução Industrial", List.of(
                new Pergunta("Em que país teve início a Revolução Industrial?",
                        List.of("França", "Alemanha", "Inglaterra", "Estados Unidos"), 2),
                new Pergunta("Qual invenção é um marco da Primeira Revolução Industrial?",
                        List.of("Máquina a vapor", "Internet", "Motor elétrico", "Telefone"), 0),
                new Pergunta("A Revolução Industrial teve início por volta de que século?",
                        List.of("XVI", "XVII", "XVIII", "XX"), 2)));

        PERGUNTAS_POR_DESAFIO.put("Ecossistemas e Biomas", List.of(
                new Pergunta("Qual bioma brasileiro é conhecido pela maior biodiversidade?",
                        List.of("Caatinga", "Cerrado", "Amazônia", "Pampa"), 2),
                new Pergunta("O que caracteriza um ecossistema?",
                        List.of("Somente seres vivos", "Somente fatores abióticos",
                                "Interação entre seres vivos e ambiente", "Apenas o clima"), 2),
                new Pergunta("Qual bioma é caracterizado pela vegetação adaptada à seca, no nordeste brasileiro?",
                        List.of("Pantanal", "Caatinga", "Mata Atlântica", "Pampa"), 1)));

        PERGUNTAS_POR_DESAFIO.put("Derivadas e Integrais", List.of(
                new Pergunta("A derivada de uma função constante é:",
                        List.of("A própria constante", "Zero", "Infinito", "Indefinida"), 1),
                new Pergunta("Qual é a derivada de x²?", List.of("x", "2x", "x²", "2"), 1),
                new Pergunta("A integral é o processo inverso de qual operação?",
                        List.of("Multiplicação", "Derivação", "Potenciação", "Radiciação"), 1)));
    }

    @Bean
    @Order(5) // depois do DataSeeder — os desafios (por título) precisam já existir
    CommandLineRunner semearPerguntasQuiz(DesafioRepository desafios, QuizPerguntaRepository perguntas) {
        return args -> {
            for (Desafio d : desafios.findAll()) {
                List<Pergunta> exemplos = PERGUNTAS_POR_DESAFIO.get(d.getTitulo());
                if (exemplos == null || !perguntas.findByDesafioIdOrderById(d.getId()).isEmpty()) continue;
                for (Pergunta p : exemplos) {
                    QuizPergunta qp = new QuizPergunta();
                    qp.setDesafio(d);
                    qp.setEnunciado(p.enunciado());
                    qp.setAlternativas(p.alternativas());
                    qp.setRespostaCorreta(p.respostaCorreta());
                    perguntas.save(qp);
                }
            }
        };
    }
}
