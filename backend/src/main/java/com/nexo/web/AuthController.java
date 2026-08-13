package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.api.dto.AuthDtos.EsqueciSenhaRequest;
import com.nexo.api.dto.AuthDtos.LoginRequest;
import com.nexo.api.dto.AuthDtos.RedefinirSenhaRequest;
import com.nexo.api.dto.AuthDtos.SessaoEmitida;
import com.nexo.api.dto.AuthDtos.TokenResponse;
import com.nexo.api.dto.AuthDtos.UsuarioDTO;
import com.nexo.security.RefreshTokenCookie;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuthService;
import com.nexo.service.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RecuperacaoSenhaService recuperacao;
    private final RefreshTokenCookie refreshCookie;

    public AuthController(AuthService authService, RecuperacaoSenhaService recuperacao,
                          RefreshTokenCookie refreshCookie) {
        this.authService = authService;
        this.recuperacao = recuperacao;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        return comCookie(authService.login(request.login(), request.senha(), http.getRemoteAddr()), http);
    }

    /**
     * O refresh token vem do cookie HttpOnly, não do corpo: o cliente não tem como lê-lo
     * para montar um JSON, e é exatamente essa a intenção. O cookie é enviado pelo
     * navegador só nesta rota (ver {@link RefreshTokenCookie}).
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NOME, required = false) String refreshToken,
            HttpServletRequest http) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("Refresh token ausente.");
        }
        return comCookie(authService.refresh(refreshToken, http.getRemoteAddr()), http);
    }

    /**
     * Encerra só esta sessão. O cookie identifica qual delas — daí ele ser lido aqui
     * também, e não apenas em {@link #refresh}.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @CookieValue(name = RefreshTokenCookie.NOME, required = false) String refreshToken,
            HttpServletRequest http) {
        authService.logout(usuario, refreshToken, http.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.limpar(http.isSecure()))
                .build();
    }

    @GetMapping("/me")
    public UsuarioDTO me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return authService.me(usuario.id());
    }

    // ── Recuperação de senha ─────────────────────────────────────────────────

    /**
     * Responde 204 sempre — inclusive para login que não existe, ou que existe sem e-mail
     * de contato. É a resposta uniforme que impede o endpoint de virar um verificador de
     * quem estuda ou trabalha na escola: público e sem autenticação, ele responderia a
     * qualquer um que perguntasse. O único 4xx possível aqui é o excesso de pedidos.
     */
    @PostMapping("/senha/esqueci")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest request, HttpServletRequest http) {
        recuperacao.solicitar(request.login(), http.getRemoteAddr());
    }

    /**
     * Consome o link recebido por e-mail. Encerra as sessões abertas do usuário — ver
     * {@link com.nexo.service.RecuperacaoSenhaService#redefinir}. O cookie de refresh desta
     * requisição também é limpo: se quem redefiniu tinha uma sessão aberta neste navegador,
     * ela acabou de ser revogada e o cookie só sobraria para tomar 401 no próximo uso.
     */
    @PostMapping("/senha/redefinir")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request,
                                               HttpServletRequest http) {
        recuperacao.redefinir(request.token(), request.novaSenha(), http.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.limpar(http.isSecure()))
                .build();
    }

    /** Devolve o corpo da sessão e manda o refresh token recém-emitido no cookie. */
    private ResponseEntity<TokenResponse> comCookie(SessaoEmitida sessao, HttpServletRequest http) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie.emitir(sessao.refreshTokenPlano(), http.isSecure()))
                .body(sessao.resposta());
    }
}
