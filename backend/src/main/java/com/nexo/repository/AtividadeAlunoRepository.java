package com.nexo.repository;

import com.nexo.domain.AtividadeAluno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AtividadeAlunoRepository extends JpaRepository<AtividadeAluno, Long> {
    List<AtividadeAluno> findTop6ByAlunoIdOrderByCriadaEmDesc(Long alunoId);
}
