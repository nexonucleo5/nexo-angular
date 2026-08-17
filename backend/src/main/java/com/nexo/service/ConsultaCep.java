package com.nexo.service;

import com.nexo.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Busca de endereço por CEP.
 *
 * <p><b>Por que a BrasilAPI e não o ViaCEP direto:</b> a BrasilAPI não é uma base
 * própria — ela consulta vários provedores (ViaCEP, Correios, Widenet) e devolve o
 * primeiro que responder. Ou seja, ela já traz a redundância de graça, enquanto o
 * ViaCEP sozinho é um ponto único de falha. O ViaCEP fica aqui como reserva para o
 * caso de a própria BrasilAPI estar fora, e assim a busca só falha de verdade se os
 * dois caírem juntos. As duas são públicas, sem chave e sem cadastro.
 *
 * <p><b>Por que no servidor e não no navegador:</b> a CSP da aplicação declara
 * {@code connect-src 'self'} (ver SecurityConfig), então o navegador é bloqueado ao
 * tentar falar com qualquer host externo. Chamar daqui mantém a CSP fechada, deixa o
 * contrato do frontend estável se o provedor mudar e permite o cache abaixo.
 */
@Service
public class ConsultaCep {

    /** O que a aplicação devolve, independente de quem respondeu. */
    public record EnderecoCep(String cep, String logradouro, String bairro,
                              String cidade, String uf) {}

    private final RestClient http;
    private final String urlBrasilApi;
    private final String urlViaCep;

    /**
     * CEP praticamente não muda, e a mesma secretaria repete o CEP do bairro da
     * escola o dia inteiro — o cache evita ida à rede e é gentil com um serviço
     * público e gratuito. Sem TTL de propósito: o processo reinicia com frequência
     * (o PaaS hiberna) e a poda abaixo segura o tamanho.
     */
    private final Map<String, EnderecoCep> cache = new ConcurrentHashMap<>();
    private static final int LIMITE_CACHE = 500;

    public ConsultaCep(
            @Value("${nexo.cep.brasilapi-url:https://brasilapi.com.br/api/cep/v1}") String urlBrasilApi,
            @Value("${nexo.cep.viacep-url:https://viacep.com.br/ws}") String urlViaCep,
            @Value("${nexo.cep.timeout-ms:2500}") int timeoutMs) {
        this.urlBrasilApi = urlBrasilApi;
        this.urlViaCep = urlViaCep;

        // Timeout curto e explícito: sem ele o default é "esperar para sempre", e um
        // provedor lento prenderia a thread do Tomcat que atende o cadastro.
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(timeoutMs));
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    /** Só os dígitos; valida as 8 posições e recusa o CEP genérico 00000000. */
    public static String normalizar(String cep) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != 8 || digitos.chars().allMatch(c -> c == '0')) {
            throw ApiException.validation("CEP inválido.",
                    Map.of("cep", "Informe os 8 dígitos do CEP."));
        }
        return digitos;
    }

    public EnderecoCep buscar(String cepInformado) {
        String cep = normalizar(cepInformado);

        EnderecoCep emCache = cache.get(cep);
        if (emCache != null) return emCache;

        EnderecoCep encontrado = viaBrasilApi(cep);
        if (encontrado == null) encontrado = viaViaCep(cep);

        if (encontrado == null) {
            // Os dois provedores fora do ar não é erro de quem pediu: 503 diz ao
            // cliente que a falha é temporária e que o cadastro manual segue válido.
            throw ApiException.servicoIndisponivel(
                    "Não foi possível consultar o CEP agora. Preencha o endereço manualmente.");
        }

        if (cache.size() >= LIMITE_CACHE) cache.clear();
        cache.put(cep, encontrado);
        return encontrado;
    }

    /**
     * Resposta da BrasilAPI: {"cep","state","city","neighborhood","street"}.
     * 404 dela significa CEP inexistente em todos os provedores que ela consultou —
     * isso é resposta, não falha, então vira 404 para o cliente em vez de tentar a
     * reserva com a mesma pergunta.
     */
    private EnderecoCep viaBrasilApi(String cep) {
        try {
            Map<String, Object> corpo = http.get()
                    .uri(urlBrasilApi + "/" + cep)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw ApiException.notFound("CEP não encontrado.");
                    })
                    .body(MAPA);
            return corpo == null ? null : new EnderecoCep(
                    cep,
                    texto(corpo.get("street")),
                    texto(corpo.get("neighborhood")),
                    texto(corpo.get("city")),
                    texto(corpo.get("state")));
        } catch (ApiException e) {
            throw e; // 404 de CEP inexistente sobe; não é caso de reserva
        } catch (Exception e) {
            return null; // rede/timeout/5xx: tenta a reserva
        }
    }

    /**
     * Resposta do ViaCEP: {"logradouro","bairro","localidade","uf"} e, para CEP que
     * não existe, HTTP 200 com {"erro": true} — daí a checagem explícita.
     */
    private EnderecoCep viaViaCep(String cep) {
        try {
            Map<String, Object> corpo = http.get()
                    .uri(urlViaCep + "/" + cep + "/json/")
                    .retrieve()
                    .body(MAPA);
            if (corpo == null || corpo.containsKey("erro")) {
                throw ApiException.notFound("CEP não encontrado.");
            }
            return new EnderecoCep(
                    cep,
                    texto(corpo.get("logradouro")),
                    texto(corpo.get("bairro")),
                    texto(corpo.get("localidade")),
                    texto(corpo.get("uf")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAPA =
            new org.springframework.core.ParameterizedTypeReference<>() {};

    /** Campo ausente ou em branco vira null — o cliente trata um caso só. */
    private static String texto(Object valor) {
        if (valor == null) return null;
        String s = String.valueOf(valor).trim();
        return s.isEmpty() ? null : s;
    }
}
