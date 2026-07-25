package com.nexo.repository;

import com.nexo.domain.FotoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FotoPerfilRepository extends JpaRepository<FotoPerfil, String> {
    Optional<FotoPerfil> findByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
