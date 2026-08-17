package com.nexo.web;

import com.nexo.service.ConsultaCep;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Consulta de endereço por CEP para a tela de cadastro.
 *
 * <p>Quem cadastra aluno é a secretaria (e o diretor, que supervisiona) — mesmos
 * papéis de POST /api/alunos. Não é rota pública: aberta, seria um proxy de graça
 * para a API de terceiro, com o IP da escola aparecendo como origem do abuso.
 */
@RestController
@RequestMapping("/api/cep")
@PreAuthorize("hasAnyRole('SECRETARIA','DIRETOR')")
public class CepController {

    private final ConsultaCep consultaCep;

    public CepController(ConsultaCep consultaCep) {
        this.consultaCep = consultaCep;
    }

    @GetMapping("/{cep}")
    public ResponseEntity<ConsultaCep.EnderecoCep> buscar(@PathVariable String cep) {
        // Cache no cliente também: quem corrige um dígito e volta ao CEP anterior não
        // provoca nova ida à rede. Privado porque a resposta segue um recurso autenticado.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePrivate())
                .body(consultaCep.buscar(cep));
    }
}
