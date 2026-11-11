package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.security.WsTicketService;
import com.nexo.service.ChatService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Histórico e contatos do chat direto. Mensagem só de texto vai pelo WebSocket;
 * mensagem com anexo (arquivo ou áudio) e o apagar passam por aqui e são
 * distribuídos ao vivo pelo {@link ChatWebSocketHandler}.
 */
@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasAnyRole('ALUNO','PROFESSOR','DIRETOR')")
public class ChatController {

    /**
     * Tipos que o navegador pode exibir direto (imagem, áudio, PDF). Qualquer outro é
     * servido como binário genérico. SVG fica de fora de propósito: pode carregar script.
     */
    private static final Set<String> TIPOS_EXIBIVEIS = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp",
            "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav", "audio/aac",
            "application/pdf");

    private final ChatService chat;
    private final WsTicketService wsTickets;
    private final ChatWebSocketHandler ws;

    public ChatController(ChatService chat, WsTicketService wsTickets, ChatWebSocketHandler ws) {
        this.chat = chat;
        this.wsTickets = wsTickets;
        this.ws = ws;
    }

    @GetMapping("/contatos")
    public List<ChatService.ContatoDTO> contatos(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return chat.contatos(principal.id());
    }

    @GetMapping("/{outroId}")
    public List<ChatService.ChatMensagemDTO> historico(@AuthenticationPrincipal UsuarioAutenticado principal,
                                                       @PathVariable Long outroId) {
        return chat.historico(principal.id(), outroId);
    }

    /** Mensagem com até 3 anexos (arquivos e/ou áudio gravado) e texto opcional. */
    @PostMapping(value = "/{outroId}/mensagens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatService.ChatMensagemDTO enviarComAnexos(@AuthenticationPrincipal UsuarioAutenticado principal,
                                                       @PathVariable Long outroId,
                                                       @RequestParam(value = "texto", required = false) String texto,
                                                       @RequestPart(value = "arquivos", required = false)
                                                       List<MultipartFile> arquivos) {
        ChatService.ChatMensagemDTO salva =
                chat.salvarComAnexos(principal.id(), principal.nome(), outroId, texto, arquivos);
        ws.distribuir(salva.de(), salva.para(), salva);
        return salva;
    }

    /** Apaga a mensagem para os dois lados. Só quem enviou pode apagar. */
    @DeleteMapping("/mensagens/{id}")
    public ResponseEntity<Void> apagar(@AuthenticationPrincipal UsuarioAutenticado principal,
                                       @PathVariable Long id) {
        ChatService.MensagemApagadaDTO evento = chat.apagar(principal.id(), id);
        ws.distribuir(evento.de(), evento.para(), evento);
        return ResponseEntity.noContent().build();
    }

    /** Apaga a mensagem só para quem pediu (própria ou recebida); o outro continua vendo. */
    @PostMapping("/mensagens/{id}/ocultar")
    public ResponseEntity<Void> ocultar(@AuthenticationPrincipal UsuarioAutenticado principal,
                                        @PathVariable Long id) {
        ChatService.MensagemOcultadaDTO evento = chat.ocultar(principal.id(), id);
        ws.enviarAo(principal.id(), evento); // sincroniza as outras abas de quem apagou
        return ResponseEntity.noContent().build();
    }

    /**
     * Download de um anexo, só para os participantes da conversa. Sempre como
     * "attachment" e com nosniff: abrir a URL direto nunca renderiza o arquivo na
     * origem da aplicação. A tela busca os bytes via HttpClient (com o token) e
     * exibe imagem/áudio a partir de um blob.
     */
    @GetMapping("/anexos/{id}")
    public ResponseEntity<byte[]> anexo(@AuthenticationPrincipal UsuarioAutenticado principal,
                                        @PathVariable String id) {
        ChatService.ConteudoAnexo a = chat.anexo(principal.id(), id);
        MediaType tipo = TIPOS_EXIBIVEIS.contains(a.tipo())
                ? MediaType.parseMediaType(a.tipo()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(a.nome(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(a.dados());
    }

    /** Ticket de uso único (30s) para o handshake do WebSocket — ver WsTicketService. */
    @GetMapping("/ws-ticket")
    public Map<String, String> wsTicket(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return Map.of("ticket", wsTickets.emitir(principal.id(), principal.nome()));
    }
}
