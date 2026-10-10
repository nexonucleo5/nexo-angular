package com.nexo.repository;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLoginIgnoreCase(String login);
    boolean existsByLoginIgnoreCase(String login);
    List<Usuario> findByRoleInOrderByNome(List<Role> roles);

    /** Contas para a tela de administração: filtro opcional por papel e por nome/login. */
    @Query("""
           select u from Usuario u
           where (:role is null or u.role = :role)
             and (:busca is null or lower(u.nome) like :busca or lower(u.login) like :busca)
           order by u.nome
           """)
    org.springframework.data.domain.Page<Usuario> buscar(@Param("role") Role role,
                                                         @Param("busca") String busca,
                                                         org.springframework.data.domain.Pageable pageable);

    /** Sem uso em produção desde o group by de contarPorPapelEAtivo(): fica como referência independente no AgregadosEContagensTest. */
    long countByRole(Role role);

    /** Sem uso em produção desde o group by de contarPorPapelEAtivo(): fica como referência independente no AgregadosEContagensTest. */
    long countByAtivo(boolean ativo);

    /**
     * (papel, ativo, total) para a escola inteira numa consulta só.
     *
     * <p>O painel do administrador precisa de seis números sobre a tabela de usuários —
     * total, inativos e um por papel. Eram seis {@code count(*)} separados, ou seja seis
     * idas ao banco (e seis varreduras) para responder o que um único {@code group by}
     * responde. O agrupamento também é por {@code ativo} porque a conta de inativos sai
     * do mesmo resultado.
     */
    @Query("select u.role, u.ativo, count(u) from Usuario u group by u.role, u.ativo")
    List<Object[]> contarPorPapelEAtivo();

    /**
     * Só o papel, sem materializar a entidade. Usado na checagem que roda a cada
     * mensagem do chat: antes cada mensagem carregava dois {@code Usuario} completos
     * para o contexto de persistência apenas para comparar dois enums.
     */
    @Query("select u.role from Usuario u where u.id = :id")
    Optional<Role> findRoleById(@Param("id") Long id);
}
