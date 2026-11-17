package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.repository.FotoPerfilRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Entrega das fotos de perfil. Público de propósito: a tag &lt;img&gt; não envia o
 * header Authorization. O id é um UUID aleatório, então não dá para varrer as fotos
 * dos usuários por id sequencial, e ele muda a cada novo envio (cache-busting).
 */
@RestController
public class FotosController {

    private final FotoPerfilRepository fotos;

    public FotosController(FotoPerfilRepository fotos) {
        this.fotos = fotos;
    }

    @GetMapping("/api/fotos/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> foto(@PathVariable String id) {
        FotoPerfilRepository.Conteudo foto = fotos.findConteudoById(id)
                .orElseThrow(() -> ApiException.notFound("Foto não encontrada."));

        return ResponseEntity.ok()
                // O id nunca é reaproveitado, então a imagem pode ser cacheada por muito tempo.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(foto.getTipoConteudo()))
                // Defesa em profundidade para o upload: o tipo é conferido pelos primeiros
                // bytes, então um arquivo com cabeçalho de imagem e o resto arbitrário passa.
                // Servido como imagem ele é inofensivo; este CSP garante que, mesmo aberto
                // direto numa aba, nada nele execute ou carregue recurso. O nosniff já vem do
                // Spring Security, e impede o navegador de "adivinhar" outro tipo.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(foto.getDados());
    }
}
