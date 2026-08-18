package com.nexo.repository;

import com.nexo.domain.DocumentoEntregue;
import com.nexo.domain.TipoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoEntregueRepository extends JpaRepository<DocumentoEntregue, Long> {

    List<DocumentoEntregue> findByMatriculaId(Long matriculaId);

    Optional<DocumentoEntregue> findByMatriculaIdAndTipo(Long matriculaId, TipoDocumento tipo);
}
