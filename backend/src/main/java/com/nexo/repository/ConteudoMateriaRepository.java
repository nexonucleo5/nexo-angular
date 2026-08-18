package com.nexo.repository;

import com.nexo.domain.ConteudoMateria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConteudoMateriaRepository extends JpaRepository<ConteudoMateria, Long> {
    List<ConteudoMateria> findByMateriaIdOrderByOrdemAsc(Long materiaId);

    /**
     * Só o que está no ar — é o recorte da tela do aluno. O {@code is null} cobre o
     * conteúdo gravado antes da coluna existir, que vale como publicado.
     */
    @org.springframework.data.jpa.repository.Query("""
           select c from ConteudoMateria c
           where c.materia.id = :materiaId and (c.publicado is null or c.publicado = true)
           order by c.ordem asc
           """)
    List<ConteudoMateria> publicadosDaMateria(
            @org.springframework.data.repository.query.Param("materiaId") Long materiaId);

    long countByPublicado(Boolean publicado);
    boolean existsByMateriaId(Long materiaId);

    /**
     * (materiaId, total de conteúdos publicados) — denominador do progresso, numa
     * consulta só. Conta só o que está no ar: com o despublicado no denominador o
     * aluno ficaria travado abaixo de 100% por causa de um conteúdo que ele não
     * tem como abrir.
     */
    @org.springframework.data.jpa.repository.Query("""
           select c.materia.id, count(c) from ConteudoMateria c
           where c.publicado is null or c.publicado = true
           group by c.materia.id
           """)
    List<Object[]> totalPorMateria();
}
