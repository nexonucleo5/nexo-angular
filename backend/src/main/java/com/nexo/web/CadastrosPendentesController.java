package com.nexo.web;

import com.nexo.domain.CadastroPendente;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AlunoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Fila de aprovação de cadastro de aluno — o outro lado da configuração "Exigir
 * aprovação manual de cadastro de aluno". Quem cadastra com ela ligada deixa um pedido
 * aqui; aprovar é o que cria a conta e devolve o acesso, rejeitar encerra o pedido sem
 * criar nada.
 */
@RestController
@RequestMapping("/api/cadastros-pendentes")
@PreAuthorize("hasAnyRole('DIRETOR','ADMIN')")
public class CadastrosPendentesController {

    private final AlunoService alunos;

    public CadastrosPendentesController(AlunoService alunos) {
        this.alunos = alunos;
    }

    public record PedidoDTO(Long id, String nome, Long turmaId, String turma, String solicitadoPor,
                            String situacao, Instant criadoEm) {
        static PedidoDTO of(CadastroPendente c) {
            return new PedidoDTO(c.getId(), c.getNome(), c.getTurma().getId(), c.getTurma().getNome(),
                    c.getSolicitadoPor(), c.getSituacao().name(), c.getCriadoEm());
        }
    }

    public record ContagemDTO(long pendentes) {}

    @GetMapping
    public List<PedidoDTO> listar() {
        return alunos.listarPendentes().stream().map(PedidoDTO::of).toList();
    }

    /** Só o número, para o painel: não vale trazer a fila inteira para exibir um contador. */
    @GetMapping("/contagem")
    public ContagemDTO contagem() {
        return new ContagemDTO(alunos.contarPendentes());
    }

    /**
     * Cria a conta. A resposta traz a senha provisória, que só existe aqui — como no
     * cadastro direto, quem aprova precisa repassá-la agora.
     */
    @PostMapping("/{id}/aprovar")
    public AlunoService.AlunoCriado aprovar(@PathVariable Long id,
                                            @AuthenticationPrincipal UsuarioAutenticado operador) {
        return alunos.aprovar(id, operador.nome());
    }

    @PostMapping("/{id}/rejeitar")
    public PedidoDTO rejeitar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado operador) {
        CadastroPendente pedido = alunos.rejeitar(id, operador.nome());
        return new PedidoDTO(pedido.getId(), pedido.getNome(), pedido.getTurma().getId(), null,
                pedido.getSolicitadoPor(), CadastroPendente.Situacao.REJEITADO.name(), pedido.getCriadoEm());
    }
}
