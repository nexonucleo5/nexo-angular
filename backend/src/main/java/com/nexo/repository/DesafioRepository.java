package com.nexo.repository;

import com.nexo.domain.Desafio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DesafioRepository extends JpaRepository<Desafio, Long> {

    /** Catálogo do administrador: publicados e despublicados, agrupados por matéria. */
    List<Desafio> findAllByOrderByMateriaAscTituloAsc();

    /** Sem uso em produção desde o group by de contarPorPublicado(): fica como referência independente no AgregadosEContagensTest. */
    long countByPublicado(Boolean publicado);

    /**
     * (publicado, total) numa consulta só — o painel do administrador mostra o total e
     * os despublicados lado a lado, e eram dois {@code count(*)} sobre a mesma tabela.
     * O {@code publicado} nulo (registro anterior à coluna) vem como chave própria e
     * conta como publicado, igual ao resto do sistema.
     */
    @org.springframework.data.jpa.repository.Query("select d.publicado, count(d) from Desafio d group by d.publicado")
    java.util.List<Object[]> contarPorPublicado();
}
