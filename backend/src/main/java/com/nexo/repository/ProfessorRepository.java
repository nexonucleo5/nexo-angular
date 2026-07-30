package com.nexo.repository;

import com.nexo.domain.Professor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByUsuarioId(Long usuarioId);

    boolean existsByEmail(String email);

    /**
     * Docentes com matérias e conta de usuário já carregadas — evita um SELECT por
     * professor no monitoramento. O usuario entra no fetch porque o monitoramento
     * expõe o id dele (é por ele que o chat identifica o interlocutor) e a relação
     * é LAZY: sem o fetch, lê-la fora da transação estoura.
     * O join é left: professor sem conta de usuário continua aparecendo na lista.
     */
    @Query("select distinct p from Professor p left join fetch p.materias left join fetch p.usuario order by p.nome")
    List<Professor> findAllComMaterias();
}
