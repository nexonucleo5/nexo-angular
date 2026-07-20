package com.nexo.repository;

import com.nexo.domain.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {
    boolean existsByCpf(String cpf);
    boolean existsByEmailInstitucional(String email);
    Optional<Aluno> findByUsuarioId(Long usuarioId);
    List<Aluno> findByTurmaIdOrderByNome(Long turmaId);
}
