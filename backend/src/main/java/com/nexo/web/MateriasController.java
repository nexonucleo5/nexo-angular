package com.nexo.web;

import com.nexo.domain.Materia;
import com.nexo.repository.MateriaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/materias")
public class MateriasController {

    private final MateriaRepository materias;

    public MateriasController(MateriaRepository materias) {
        this.materias = materias;
    }

    public record MateriaDTO(Long id, String nome) {
        static MateriaDTO of(Materia m) { return new MateriaDTO(m.getId(), m.getNome()); }
    }

    /** Catálogo de matérias — alimenta a seleção múltipla do cadastro de professor. */
    @GetMapping
    public List<MateriaDTO> listar() {
        return materias.findAllByOrderByNome().stream().map(MateriaDTO::of).toList();
    }
}
