package com.nexo.web;

import com.nexo.domain.ConteudoMateria;
import com.nexo.domain.Materia;
import com.nexo.repository.ConteudoMateriaRepository;
import com.nexo.repository.MateriaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/materias")
public class MateriasController {

    private final MateriaRepository materias;
    private final ConteudoMateriaRepository conteudos;

    public MateriasController(MateriaRepository materias, ConteudoMateriaRepository conteudos) {
        this.materias = materias;
        this.conteudos = conteudos;
    }

    public record MateriaDTO(Long id, String nome) {
        static MateriaDTO of(Materia m) { return new MateriaDTO(m.getId(), m.getNome()); }
    }

    public record ConteudoMateriaDTO(Long id, String titulo, String resumo, String texto,
                                     String exemplo, int minutos, int ordem) {
        static ConteudoMateriaDTO of(ConteudoMateria c) {
            // minutos é nulo em conteúdo gravado antes do campo existir (ver
            // ConteudoMateria); 0 esconde a etiqueta de tempo em vez de estourar.
            return new ConteudoMateriaDTO(c.getId(), c.getTitulo(), c.getResumo(), c.getTexto(),
                    c.getExemplo(), c.getMinutos() == null ? 0 : c.getMinutos(), c.getOrdem());
        }
    }

    /** Catálogo de matérias — alimenta a seleção múltipla do cadastro de professor. */
    @GetMapping
    public List<MateriaDTO> listar() {
        return materias.findAllByOrderByNome().stream().map(MateriaDTO::of).toList();
    }

    /**
     * Conteúdo/documentos da matéria, para a tela do aluno. Matérias sem
     * conteúdo cadastrado devolvem lista vazia — o client trata esse caso.
     */
    @GetMapping("/{id}/conteudos")
    public List<ConteudoMateriaDTO> conteudos(@PathVariable Long id) {
        return conteudos.findByMateriaIdOrderByOrdemAsc(id).stream().map(ConteudoMateriaDTO::of).toList();
    }
}
