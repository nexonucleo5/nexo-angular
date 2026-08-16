package com.nexo.repository;

import com.nexo.domain.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {
    boolean existsByEmailInstitucional(String email);
    Optional<Aluno> findByUsuarioId(Long usuarioId);
    List<Aluno> findByTurmaIdOrderByNome(Long turmaId);

    /**
     * Alunos com a turma já carregada — evita um SELECT lazy por turma ao montar DTOs.
     * Ordena por id para reproduzir a ordem de {@code findAll()}: listas como a de evasão
     * são ordenadas por risco com sort estável, então o desempate vem daqui.
     */
    @Query("select a from Aluno a left join fetch a.turma order by a.id")
    List<Aluno> findAllComTurma();

    /** Alunos de várias turmas de uma vez — evita uma query por turma dentro de laços. */
    @Query("select a from Aluno a left join fetch a.turma t where t.id in :turmaIds order by a.nome")
    List<Aluno> findByTurmaIdInComTurma(Collection<Long> turmaIds);

    /**
     * Só as quatro colunas que o ranking de gamificação exibe, já ordenadas pelo banco.
     * O dashboard mostra 5 posições, mas precisa varrer a escola inteira para achar a
     * colocação do aluno logado — com {@code findAll()} isso trazia toda a tabela como
     * entidade gerenciada (cada uma com o snapshot de dirty-checking do Hibernate junto).
     * O desempate por id reproduz a ordem estável do sort que existia em Java.
     */
    interface RankingXp {
        Long getId();
        String getNome();
        int getXpTotal();
        String getFoto();
    }

    @Query("""
           select a.id as id, a.nome as nome, a.xpTotal as xpTotal, a.foto as foto
           from Aluno a
           order by a.xpTotal desc, a.id asc
           """)
    List<RankingXp> rankingPorXp();
}
