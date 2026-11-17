package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.Aviso;
import com.nexo.domain.Turma;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.ProfessorRepository;
import com.nexo.repository.TurmaRepository;
import com.nexo.security.UsuarioAutenticado;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Quem pode ler cada aviso, e para quem um aviso pode ser endereçado.
 *
 * <p>O {@code destino} de um aviso é "Todos" ou o nome de uma turma. Antes ele era só um
 * rótulo: {@code GET /api/avisos} devolvia todos os avisos da escola a qualquer usuário
 * autenticado — um aluno lia o aviso que o professor mandou a outra turma — e o campo
 * aceitava qualquer texto na publicação. Aqui o rótulo vira regra, aplicada no servidor
 * pelo mesmo motivo de {@code TurmasController.listar}: filtro de tela é conveniência, não
 * controle de acesso.
 *
 * <ul>
 *   <li>DIRETOR e ADMIN veem todos (o diretor precisa auditar o que os professores mandam);</li>
 *   <li>PROFESSOR vê os gerais e os das turmas que leciona;</li>
 *   <li>ALUNO vê os gerais e os da própria turma.</li>
 * </ul>
 */
@Component
public class EscopoAvisos {

    public static final String DESTINO_GERAL = "Todos";

    /** "Todas as turmas" é o rótulo que a tela já usava quando o destino vinha vazio. */
    private static final Set<String> GERAIS = Set.of("todos", "todas as turmas");

    private final AlunoRepository alunos;
    private final ProfessorRepository professores;
    private final TurmaRepository turmas;

    public EscopoAvisos(AlunoRepository alunos, ProfessorRepository professores, TurmaRepository turmas) {
        this.alunos = alunos;
        this.professores = professores;
        this.turmas = turmas;
    }

    /**
     * Destinos (em minúsculas) que o operador pode ler, já incluindo os gerais; {@code null}
     * quando não há restrição. Papel sem vínculo com turma enxerga só os gerais — e não a
     * escola inteira.
     */
    @Transactional(readOnly = true)
    public Set<String> destinosVisiveis(UsuarioAutenticado operador) {
        String role = operador.role();
        if ("DIRETOR".equals(role) || "ADMIN".equals(role)) return null;

        Set<String> destinos = new HashSet<>(GERAIS);
        if ("PROFESSOR".equals(role)) {
            professores.findByUsuarioId(operador.id()).ifPresent(p ->
                    turmas.findByProfessorIdOrderByNome(p.getId())
                            .forEach(t -> destinos.add(normalizar(t.getNome()))));
        } else if ("ALUNO".equals(role)) {
            alunos.findByUsuarioId(operador.id()).ifPresent(a -> {
                if (a.getTurma() != null) destinos.add(normalizar(a.getTurma().getNome()));
            });
        }
        return destinos;
    }

    /** O aviso é para este conjunto de destinos? {@code null} = sem restrição. */
    public boolean visivel(Aviso aviso, Set<String> destinosVisiveis) {
        if (destinosVisiveis == null) return true;
        String destino = aviso.getDestino();
        if (destino == null || destino.isBlank()) return true;
        return destinosVisiveis.contains(normalizar(destino));
    }

    /**
     * Destino de um aviso novo. Vazio vale "Todos"; qualquer outro valor precisa ser uma
     * turma existente — e, para o PROFESSOR, uma turma que ele leciona, a mesma regra de
     * {@code exigirLeciona} nas demais escritas por turma.
     */
    @Transactional(readOnly = true)
    public String destinoParaPublicar(String pedido, UsuarioAutenticado autor) {
        if (pedido == null || pedido.isBlank()) return DESTINO_GERAL;
        String destino = pedido.trim();
        if (GERAIS.contains(normalizar(destino))) return DESTINO_GERAL;

        Turma turma = turmas.findByNome(destino).orElseThrow(() ->
                ApiException.badRequest("Turma de destino inexistente."));
        if ("PROFESSOR".equals(autor.role())) {
            boolean leciona = professores.findByUsuarioId(autor.id())
                    .map(p -> turma.getProfessor() != null && turma.getProfessor().getId().equals(p.getId()))
                    .orElse(false);
            if (!leciona) {
                throw ApiException.forbidden("Você não leciona a turma de destino do aviso.");
            }
        }
        return turma.getNome();
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
    }
}
