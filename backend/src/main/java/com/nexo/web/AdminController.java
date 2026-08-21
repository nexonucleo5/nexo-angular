package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.api.PageEnvelope;
import com.nexo.domain.ConteudoMateria;
import com.nexo.domain.Desafio;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Materia;
import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.ConteudoMateriaRepository;
import com.nexo.repository.DesafioRepository;
import com.nexo.repository.MateriaRepository;
import com.nexo.repository.RefreshTokenRepository;
import com.nexo.repository.TurmaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuditoriaService;
import com.nexo.service.CredenciaisService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Painel do administrador do sistema de aprendizado.
 *
 * <p>Ocupou o lugar de /api/secretaria, e a troca de assunto é o ponto: no lugar de
 * fila de matrícula, documentação a cobrar e vagas por turma, o que existe aqui é
 * <b>quem tem acesso</b> e <b>o que está publicado</b>. Nenhum dos dois é dado
 * pessoal — são conta e catálogo.
 *
 * <p>O DIRETOR também enxerga, como já enxergava a secretaria.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN','DIRETOR')")
@Transactional
public class AdminController {

    private final UsuarioRepository usuarios;
    private final TurmaRepository turmas;
    private final MateriaRepository materias;
    private final ConteudoMateriaRepository conteudos;
    private final DesafioRepository desafios;
    private final RefreshTokenRepository refreshTokens;
    private final CredenciaisService credenciais;
    private final AuditoriaService auditoria;

    public AdminController(UsuarioRepository usuarios, TurmaRepository turmas, MateriaRepository materias,
                           ConteudoMateriaRepository conteudos, DesafioRepository desafios,
                           RefreshTokenRepository refreshTokens, CredenciaisService credenciais,
                           AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.turmas = turmas;
        this.materias = materias;
        this.conteudos = conteudos;
        this.desafios = desafios;
        this.refreshTokens = refreshTokens;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
    }

    // ── Visão geral ──────────────────────────────────────────────────────────

    public record DashboardAdminDTO(long contas, long contasInativas,
                                    long alunos, long professores, long diretores, long admins,
                                    long turmas, long materias,
                                    long conteudos, long conteudosDespublicados,
                                    long desafios, long desafiosDespublicados) {}

    /**
     * Os números que orientam o trabalho de quem administra: acesso de um lado,
     * catálogo do outro. Despublicados aparecem à parte porque são o item de ação —
     * conteúdo fora do ar é conteúdo que ninguém está estudando.
     */
    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public DashboardAdminDTO dashboard() {
        return new DashboardAdminDTO(
                usuarios.count(),
                usuarios.countByAtivo(false),
                usuarios.countByRole(Role.ALUNO),
                usuarios.countByRole(Role.PROFESSOR),
                usuarios.countByRole(Role.DIRETOR),
                usuarios.countByRole(Role.ADMIN),
                turmas.count(),
                materias.count(),
                conteudos.count(),
                conteudos.countByPublicado(false),
                desafios.count(),
                desafios.countByPublicado(false));
    }

    // ── Contas e acesso ──────────────────────────────────────────────────────

    /**
     * Uma conta como o administrador precisa vê-la.
     *
     * <p>Nome e login estão aqui porque sem eles não há como saber de quem é a conta
     * que se vai desativar. É onde a lista para: não há nascimento, documento nem
     * endereço para expor, porque o sistema não os guarda.
     */
    public record ContaDTO(Long id, String login, String nome, String cargo,
                           String papel, boolean ativo, Instant criadoEm) {
        static ContaDTO of(Usuario u) {
            return new ContaDTO(u.getId(), u.getLogin(), u.getNome(), u.getCargo(),
                    u.getRole().name(), u.isAtivo(), u.getCriadoEm());
        }
    }

    @GetMapping("/contas")
    @Transactional(readOnly = true)
    public PageEnvelope<ContaDTO> contas(@RequestParam(required = false) Role papel,
                                         @RequestParam(required = false) String busca,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim().toLowerCase() + "%";
        var resultado = usuarios.buscar(papel, filtro, PageRequest.of(page, Math.min(size, 100)));
        return PageEnvelope.of(resultado, ContaDTO::of);
    }

    public record AtualizarAtivoRequest(Boolean ativo) {}

    /**
     * Liga ou desliga o acesso. Desativar derruba também as sessões abertas: sem
     * isso o refresh token na mão de quem foi desligado renovaria o acesso por
     * dias, e "conta desativada" seria só um rótulo na tela.
     *
     * <p>O access token já emitido sobrevive até expirar (15 minutos, ver
     * {@code nexo.jwt.access-token-minutes}) — a revogação por jti é por sessão e
     * não alcança a de outro usuário.
     */
    @PatchMapping("/contas/{id}/ativo")
    public ContaDTO atualizarAtivo(@PathVariable Long id,
                                   @RequestBody AtualizarAtivoRequest request,
                                   @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (request == null || request.ativo() == null) {
            throw ApiException.badRequest("Informe se a conta fica ativa.");
        }
        // Desativar a própria conta tranca quem administra do lado de fora, e
        // reverter exigiria mexer no banco à mão.
        if (id.equals(operador.id()) && !request.ativo()) {
            throw ApiException.badRequest("Você não pode desativar a própria conta.");
        }
        Usuario usuario = exigirConta(id);
        if (usuario.isAtivo() == request.ativo()) {
            return ContaDTO.of(usuario); // idempotente
        }
        usuario.setAtivo(request.ativo());
        usuarios.save(usuario);
        if (!request.ativo()) {
            refreshTokens.revogarTodosDoUsuario(usuario.getId());
        }
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                request.ativo() ? "Conta reativada" : "Conta desativada",
                "Conta: " + usuario.getLogin(), null);
        return ContaDTO.of(usuario);
    }

    /** A senha em claro existe só nesta resposta — a mesma regra do cadastro. */
    public record SenhaRedefinidaDTO(String login, String senhaProvisoria) {}

    /**
     * Sorteia uma senha provisória para quem perdeu a dela. As sessões abertas caem
     * junto: uma redefinição costuma vir de suspeita de acesso indevido, e deixar a
     * sessão anterior de pé anularia o motivo de redefinir.
     */
    @PostMapping("/contas/{id}/senha-provisoria")
    public SenhaRedefinidaDTO redefinirSenha(@PathVariable Long id,
                                             @AuthenticationPrincipal UsuarioAutenticado operador) {
        Usuario usuario = exigirConta(id);
        String senha = credenciais.redefinirSenhaProvisoria(usuario);
        usuarios.save(usuario);
        refreshTokens.revogarTodosDoUsuario(usuario.getId());
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                "Senha provisória redefinida", "Conta: " + usuario.getLogin(), null);
        return new SenhaRedefinidaDTO(usuario.getLogin(), senha);
    }

    private Usuario exigirConta(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
    }

    // ── Catálogo de conteúdo ─────────────────────────────────────────────────

    public record MateriaCatalogoDTO(Long id, String nome, String segmento,
                                     long conteudos, long conteudosPublicados) {}

    /**
     * O catálogo por matéria, com quanto de cada uma está no ar. É a resposta para
     * "onde falta conteúdo?" — a pergunta que um sistema de retenção faz antes de
     * qualquer outra.
     */
    @GetMapping("/catalogo")
    @Transactional(readOnly = true)
    public List<MateriaCatalogoDTO> catalogo() {
        Map<Long, List<ConteudoMateria>> porMateria = conteudos.findAll().stream()
                .filter(c -> c.getMateria() != null)
                .collect(Collectors.groupingBy(c -> c.getMateria().getId()));

        return materias.findAllByOrderByNome().stream()
                .map(m -> {
                    List<ConteudoMateria> lista = porMateria.getOrDefault(m.getId(), List.of());
                    long publicados = lista.stream().filter(ConteudoMateria::isPublicado).count();
                    return new MateriaCatalogoDTO(m.getId(), m.getNome(), m.getSegmento().name(),
                            lista.size(), publicados);
                })
                .toList();
    }

    public record ConteudoAdminDTO(Long id, Long materiaId, String materia, String titulo,
                                   String resumo, int minutos, int ordem, boolean publicado) {
        static ConteudoAdminDTO of(ConteudoMateria c) {
            return new ConteudoAdminDTO(c.getId(),
                    c.getMateria() != null ? c.getMateria().getId() : null,
                    c.getMateria() != null ? c.getMateria().getNome() : null,
                    c.getTitulo(), c.getResumo(),
                    c.getMinutos() == null ? 0 : c.getMinutos(),
                    c.getOrdem(), c.isPublicado());
        }
    }

    /** Conteúdos da matéria na ordem em que o aluno os vê, publicados e não. */
    @GetMapping("/catalogo/materias/{materiaId}/conteudos")
    @Transactional(readOnly = true)
    public List<ConteudoAdminDTO> conteudosDaMateria(@PathVariable Long materiaId) {
        exigirMateria(materiaId);
        return conteudos.findByMateriaIdOrderByOrdemAsc(materiaId).stream()
                .map(ConteudoAdminDTO::of).toList();
    }

    public record AtualizarPublicadoRequest(Boolean publicado) {}

    /**
     * Tira do ar ou devolve. Não há DELETE de conteúdo aqui de propósito: apagar
     * levaria junto o registro de quem já o concluiu, e com ele o progresso que
     * este sistema existe para medir.
     */
    @PatchMapping("/catalogo/conteudos/{id}/publicado")
    public ConteudoAdminDTO publicarConteudo(@PathVariable Long id,
                                             @RequestBody AtualizarPublicadoRequest request,
                                             @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (request == null || request.publicado() == null) {
            throw ApiException.badRequest("Informe se o conteúdo fica publicado.");
        }
        ConteudoMateria conteudo = conteudos.findById(id)
                .orElseThrow(() -> ApiException.notFound("Conteúdo não encontrado."));
        if (conteudo.isPublicado() == request.publicado()) {
            return ConteudoAdminDTO.of(conteudo); // idempotente
        }
        conteudo.setPublicado(request.publicado());
        conteudos.save(conteudo);
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                request.publicado() ? "Conteúdo publicado" : "Conteúdo despublicado",
                conteudo.getTitulo(), null);
        return ConteudoAdminDTO.of(conteudo);
    }

    public record ReordenarRequest(List<Long> conteudoIds) {}

    /**
     * Reordena a matéria inteira de uma vez, e não um item por requisição: a ordem é
     * uma propriedade do conjunto, e mover o terceiro para o topo mexe na posição de
     * todos os outros. Receber a lista completa também torna a operação idempotente —
     * reenviar a mesma ordem não desloca nada.
     */
    @PatchMapping("/catalogo/materias/{materiaId}/ordem")
    public List<ConteudoAdminDTO> reordenar(@PathVariable Long materiaId,
                                            @RequestBody ReordenarRequest request,
                                            @AuthenticationPrincipal UsuarioAutenticado operador) {
        Materia materia = exigirMateria(materiaId);
        if (request == null || request.conteudoIds() == null || request.conteudoIds().isEmpty()) {
            throw ApiException.badRequest("Informe a nova ordem dos conteúdos.");
        }

        List<ConteudoMateria> atuais = conteudos.findByMateriaIdOrderByOrdemAsc(materiaId);
        Map<Long, ConteudoMateria> porId = atuais.stream()
                .collect(Collectors.toMap(ConteudoMateria::getId, Function.identity()));

        // A lista precisa ser exatamente a da matéria: uma parcial deixaria os
        // ausentes com a ordem antiga, misturados aos reordenados, e um id de outra
        // matéria mudaria a ordem de uma matéria que ninguém pediu para mexer.
        if (request.conteudoIds().size() != atuais.size()
                || !porId.keySet().containsAll(request.conteudoIds())) {
            throw ApiException.validation("Ordem inválida.",
                    Map.of("conteudoIds", "Envie todos os conteúdos da matéria, uma vez cada."));
        }

        int posicao = 0;
        for (Long id : request.conteudoIds()) {
            porId.get(id).setOrdem(posicao++);
        }
        conteudos.saveAll(atuais);
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                "Conteúdos reordenados", "Matéria: " + materia.getNome(), null);

        return conteudos.findByMateriaIdOrderByOrdemAsc(materiaId).stream()
                .map(ConteudoAdminDTO::of).toList();
    }

    public record DesafioAdminDTO(Long id, String titulo, String materia, String nivel,
                                  int xp, int tempoMin, boolean publicado) {
        static DesafioAdminDTO of(Desafio d) {
            return new DesafioAdminDTO(d.getId(), d.getTitulo(), d.getMateria(), d.getNivel(),
                    d.getXp(), d.getTempoMin(), d.isPublicado());
        }
    }

    @GetMapping("/catalogo/desafios")
    @Transactional(readOnly = true)
    public List<DesafioAdminDTO> desafios() {
        return desafios.findAllByOrderByMateriaAscTituloAsc().stream().map(DesafioAdminDTO::of).toList();
    }

    @PatchMapping("/catalogo/desafios/{id}/publicado")
    public DesafioAdminDTO publicarDesafio(@PathVariable Long id,
                                           @RequestBody AtualizarPublicadoRequest request,
                                           @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (request == null || request.publicado() == null) {
            throw ApiException.badRequest("Informe se o desafio fica publicado.");
        }
        Desafio desafio = desafios.findById(id)
                .orElseThrow(() -> ApiException.notFound("Desafio não encontrado."));
        if (desafio.isPublicado() == request.publicado()) {
            return DesafioAdminDTO.of(desafio); // idempotente
        }
        desafio.setPublicado(request.publicado());
        desafios.save(desafio);
        auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                request.publicado() ? "Desafio publicado" : "Desafio despublicado",
                desafio.getTitulo(), null);
        return DesafioAdminDTO.of(desafio);
    }

    private Materia exigirMateria(Long id) {
        return materias.findById(id)
                .orElseThrow(() -> ApiException.notFound("Matéria não encontrada."));
    }
}
