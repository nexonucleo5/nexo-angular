package com.nexo.repository;

import com.nexo.domain.Frequencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FrequenciaRepository extends JpaRepository<Frequencia, Long> {
    List<Frequencia> findByTurmaIdAndData(Long turmaId, LocalDate data);
    Optional<Frequencia> findByAlunoIdAndTurmaIdAndData(Long alunoId, Long turmaId, LocalDate data);
    long countByAlunoIdAndPresenteFalse(Long alunoId);
    long countByAlunoId(Long alunoId);
    long countByPresenteFalse();
    long count();
}
