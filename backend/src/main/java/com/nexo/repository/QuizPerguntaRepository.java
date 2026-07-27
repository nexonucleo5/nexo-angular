package com.nexo.repository;

import com.nexo.domain.QuizPergunta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizPerguntaRepository extends JpaRepository<QuizPergunta, Long> {
    List<QuizPergunta> findByDesafioIdOrderById(Long desafioId);
}
