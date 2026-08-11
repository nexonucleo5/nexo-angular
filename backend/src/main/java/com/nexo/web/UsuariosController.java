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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
public class UsuariosController {

    private static final long TAMANHO_MAXIMO = 2 * 1024 * 1024; // 2 MB

    /**
     * Assinatura dos únicos formatos que o navegador exibe direto em &lt;img&gt;, lida dos
     * primeiros bytes do arquivo. Devolve {@code null} para qualquer outra coisa.
     *
     * <p>Os bytes voltam depois em /api/fotos/{id} com o tipo declarado aqui, servidos
     * da mesma origem da aplicação — por isso o formato precisa ser confirmado no
     * conteúdo, e não aceito do cabeçalho que o cliente enviou junto.
     */
    private static String detectarTipoImagem(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        // RIFF....WEBP
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

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

        byte[] dados;
        try {
            dados = arquivo.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível ler a imagem enviada.");
        }

        // O tipo sai do conteúdo do arquivo, não do cabeçalho que o cliente mandou.
        // Antes bastava rotular qualquer arquivo como image/png para guardá-lo e
        // fazer /api/fotos/{id} devolvê-lo com esse tipo, na mesma origem da aplicação.
        String tipo = detectarTipoImagem(dados);
        if (tipo == null) {
            throw ApiException.badRequest("Formato inválido. Envie uma imagem JPEG, PNG ou WebP.");
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

    // Mutação sem corpo de resposta: 204, não 200 com corpo vazio.
    @PostMapping("/me/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
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
