package com.nexo;

import com.nexo.domain.Role;
import com.nexo.repository.*;
import com.nexo.service.AgregadosAcademicos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava as duas otimizações de carga do banco no comportamento antigo.
 *
 * <p>Ambas trocam "ler tudo e recortar em memória" por "pedir ao banco só o recorte":
 * as contagens do painel do administrador viraram {@code group by}, e os agregados de
 * nota/frequência ganharam a variante por turma. São mudanças que não quebram nada de
 * forma visível quando divergem — o número simplesmente fica errado na tela. Este teste
 * compara os dois caminhos lado a lado, para que a divergência falhe o build.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "nexo.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres-abcdef",
        "spring.datasource.url=jdbc:h2:mem:agregados;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class AgregadosEContagensTest {

    @Autowired UsuarioRepository usuarios;
    @Autowired ConteudoMateriaRepository conteudos;
    @Autowired DesafioRepository desafios;
    @Autowired AlunoRepository alunos;
    @Autowired TurmaRepository turmas;
    @Autowired AgregadosAcademicos agregados;

    @Test
    void contagensAgrupadasBatemComOsCountsAntigos() {
        long contas = 0, inativas = 0;
        var porPapel = new java.util.EnumMap<Role, Long>(Role.class);
        for (Object[] l : usuarios.contarPorPapelEAtivo()) {
            long n = ((Number) l[2]).longValue();
            contas += n;
            if (!Boolean.TRUE.equals(l[1])) inativas += n;
            porPapel.merge((Role) l[0], n, Long::sum);
        }
        assertThat(contas).isEqualTo(usuarios.count());
        assertThat(inativas).isEqualTo(usuarios.countByAtivo(false));
        for (Role r : Role.values()) {
            assertThat(porPapel.getOrDefault(r, 0L)).as("papel %s", r).isEqualTo(usuarios.countByRole(r));
        }

        assertThat(soma(conteudos.contarPorPublicado(), null)).isEqualTo(conteudos.count());
        assertThat(soma(conteudos.contarPorPublicado(), false)).isEqualTo(conteudos.countByPublicado(false));
        assertThat(soma(desafios.contarPorPublicado(), null)).isEqualTo(desafios.count());
        assertThat(soma(desafios.contarPorPublicado(), false)).isEqualTo(desafios.countByPublicado(false));
    }

    private static long soma(List<Object[]> agrupado, Boolean apenas) {
        long t = 0;
        for (Object[] l : agrupado) {
            if (apenas == null || apenas.equals(l[0])) t += ((Number) l[1]).longValue();
        }
        return t;
    }

    @Test
    void agregadosPorTurmaBatemComOsDaEscolaInteira() {
        var todos = agregados.carregar(null);
        var idsTurmas = turmas.findAll().stream().map(t -> t.getId()).toList();
        assertThat(idsTurmas).isNotEmpty();
        assertThat(alunos.findAllComTurma()).isNotEmpty(); // o seed precisa ter aluno para o teste valer

        // Por turma, um a um: cada aluno tem de receber média e faltas idênticas.
        for (Long turmaId : idsTurmas) {
            var escopo = agregados.carregarDeTurmas(List.of(turmaId), null);
            var daTurma = alunos.findByTurmaIdComTurma(turmaId);
            for (var a : daTurma) {
                assertThat(escopo.media(a.getId())).as("média do aluno %s", a.getId())
                        .isEqualTo(todos.media(a.getId()));
                assertThat(escopo.percentualFaltas(a.getId())).as("faltas do aluno %s", a.getId())
                        .isEqualTo(todos.percentualFaltas(a.getId()));
                assertThat(escopo.percentualPresenca(a.getId())).isEqualTo(todos.percentualPresenca(a.getId()));
            }
        }

        // E o conjunto de todas as turmas de uma vez reproduz a escola (menos alunos sem turma).
        var tudoEscopado = agregados.carregarDeTurmas(idsTurmas, null);
        for (var a : alunos.findAllComTurma()) {
            if (a.getTurma() == null) continue;
            assertThat(tudoEscopado.media(a.getId())).isEqualTo(todos.media(a.getId()));
            assertThat(tudoEscopado.percentualFaltas(a.getId())).isEqualTo(todos.percentualFaltas(a.getId()));
        }
    }

    @Test
    void turmaVaziaNaoVaiAoBanco() {
        var vazio = agregados.carregarDeTurmas(List.of(), null);
        assertThat(vazio.medias()).isEmpty();
        assertThat(vazio.presencas()).isEmpty();
        assertThat(vazio.mediaOuZero(1L)).isZero();
    }

    @Test
    void ordemDaTurmaEhAMesmaDaListaGeral() {
        for (var t : turmas.findAll()) {
            var esperado = alunos.findAllComTurma().stream()
                    .filter(a -> a.getTurma() != null && a.getTurma().getId().equals(t.getId()))
                    .map(a -> a.getId()).toList();
            var obtido = alunos.findByTurmaIdComTurma(t.getId()).stream().map(a -> a.getId()).toList();
            assertThat(obtido).as("ordem da turma %s", t.getId()).containsExactlyElementsOf(esperado);
        }
    }
}
