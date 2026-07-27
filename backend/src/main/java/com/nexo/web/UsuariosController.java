package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.api.dto.AuthDtos.AtualizarPerfilRequest;
import com.nexo.api.dto.AuthDtos.TrocaSenhaRequest;
import com.nexo.api.dto.AuthDtos.UsuarioDTO;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.FotoPerfil;
import com.nexo.domain.Usuario;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.FotoPerfilRepository;
import com.nexo.repository.ProfessorRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
public class UsuariosController {

    /** Só formatos que o navegador exibe direto em <img>. */
    private static final List<String> TIPOS_ACEITOS = List.of("image/jpeg", "image/png", "image/webp");
    private static final long TAMANHO_MAXIMO = 2 * 1024 * 1024; // 2 MB

    private final UsuarioRepository usuarios;
    private final FotoPerfilRepository fotos;
    private final AlunoRepository alunos;
    private final ProfessorRepository professores;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;

    public UsuariosController(UsuarioRepository usuarios, FotoPerfilRepository fotos, AlunoRepository alunos,
                              ProfessorRepository professores, PasswordEncoder passwordEncoder,
                              AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.fotos = fotos;
        this.alunos = alunos;
        this.professores = professores;
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
        // A foto não é mais aceita aqui: ela entra por POST /me/foto, como arquivo.
        // Antes, o cliente mandava um caminho arbitrário e o servidor apenas guardava.
        usuarios.save(usuario);
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO, "Perfil atualizado", null, null);
        return UsuarioDTO.of(usuario);
    }

    // ── Foto de perfil ───────────────────────────────────────────────────────

    /**
     * Recebe a foto tirada na hora ou escolhida da galeria. O cliente já reduz a
     * imagem antes de enviar; os limites aqui são a rede de segurança do servidor.
     */
    @PostMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public UsuarioDTO enviarFoto(@AuthenticationPrincipal UsuarioAutenticado principal,
                                 @RequestPart("arquivo") MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw ApiException.badRequest("Nenhuma imagem enviada.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw ApiException.badRequest("A imagem deve ter no máximo 2 MB.");
        }
        String tipo = arquivo.getContentType() == null ? "" : arquivo.getContentType().toLowerCase();
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw ApiException.badRequest("Formato inválido. Envie uma imagem JPEG, PNG ou WebP.");
        }

        byte[] dados;
        try {
            dados = arquivo.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível ler a imagem enviada.");
        }

        Usuario usuario = carregar(principal);
        // Uma foto por usuário: a anterior sai junto com o id antigo, invalidando o cache.
        fotos.deleteByUsuarioId(usuario.getId());
        fotos.flush();

        String id = UUID.randomUUID().toString().replace("-", "");
        fotos.save(new FotoPerfil(id, usuario.getId(), tipo, dados, Instant.now()));

        usuario.setFoto("/api/fotos/" + id);
        usuarios.save(usuario);
        sincronizarFotoPerfil(usuario);
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO, "Foto de perfil atualizada", null, null);
        return UsuarioDTO.of(usuario);
    }

    /** Volta ao avatar padrão. */
    @DeleteMapping("/me/foto")
    @Transactional
    public UsuarioDTO removerFoto(@AuthenticationPrincipal UsuarioAutenticado principal) {
        Usuario usuario = carregar(principal);
        fotos.deleteByUsuarioId(usuario.getId());
        usuario.setFoto(null);
        usuarios.save(usuario);
        sincronizarFotoPerfil(usuario);
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO, "Foto de perfil removida", null, null);
        return UsuarioDTO.of(usuario);
    }

    /**
     * Aluno.foto e Professor.foto são cópias denormalizadas (usadas em ranking,
     * gestão de evasão e monitoramento docente) — sem sincronizar aqui, elas ficam
     * presas na foto antiga depois de um novo envio ou remoção.
     */
    private void sincronizarFotoPerfil(Usuario usuario) {
        alunos.findByUsuarioId(usuario.getId()).ifPresent(a -> {
            a.setFoto(usuario.getFoto());
            alunos.save(a);
        });
        professores.findByUsuarioId(usuario.getId()).ifPresent(p -> {
            p.setFoto(usuario.getFoto());
            professores.save(p);
        });
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
