package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.api.dto.AuthDtos.AtualizarPerfilRequest;
import com.nexo.api.dto.AuthDtos.TrocaSenhaRequest;
import com.nexo.api.dto.AuthDtos.UsuarioDTO;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Usuario;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuariosController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;

    public UsuariosController(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
    }

    @GetMapping("/me")
    public UsuarioDTO me(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return UsuarioDTO.of(carregar(principal));
    }

    @PatchMapping("/me")
    public UsuarioDTO atualizarPerfil(@AuthenticationPrincipal UsuarioAutenticado principal,
                                      @RequestBody AtualizarPerfilRequest request) {
        Usuario usuario = carregar(principal);
        if (request.nome() != null && !request.nome().isBlank()) usuario.setNome(request.nome().trim());
        if (request.foto() != null && !request.foto().isBlank()) usuario.setFoto(request.foto().trim());
        usuarios.save(usuario);
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO, "Perfil atualizado", null, null);
        return UsuarioDTO.of(usuario);
    }

    @PostMapping("/me/senha")
    public void trocarSenha(@AuthenticationPrincipal UsuarioAutenticado principal,
                            @Valid @RequestBody TrocaSenhaRequest request) {
        Usuario usuario = carregar(principal);
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw ApiException.badRequest("Senha atual incorreta.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuarios.save(usuario);
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO, "Senha alterada", null, null);
    }

    private Usuario carregar(UsuarioAutenticado principal) {
        return usuarios.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }
}
