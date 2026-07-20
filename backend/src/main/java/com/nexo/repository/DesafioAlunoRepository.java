package com.nexo.repository;

import com.nexo.domain.DesafioAluno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesafioAlunoRepository extends JpaRepository<DesafioAluno, Long> {
    List<DesafioAluno> findByAlunoId(Long alunoId);
    Optional<DesafioAluno> findByAlunoIdAndDesafioId(Long alunoId, Long desafioId);
}
