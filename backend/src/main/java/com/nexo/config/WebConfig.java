package com.nexo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Monolito: o Spring serve o build do Angular embutido em classpath:/static/.
 *
 * Um handler só, em "/**", porque o Spring recorta o prefixo literal do padrão
 * antes de resolver o arquivo: com addResourceHandler("/assets/**") apontando
 * para classpath:/static/, o pedido /assets/logo.png é procurado em
 * static/logo.png — e some. Ou o padrão não tem prefixo literal, ou o local
 * precisa incluí-lo. Com "/**" o caminho chega inteiro e o problema não existe.
 *
 * Duas regras dentro do resolvedor:
 *
 * - Pedido com extensão de arquivo que não existe devolve 404 de verdade. Antes
 *   caía no fallback de SPA: um index.html velho no cache do navegador pedia
 *   /main-HASHVELHO.js, o servidor respondia 200 com o index.html, e o navegador
 *   recebia "text/html" onde esperava JavaScript. Script não executa, folha de
 *   estilo é ignorada, e nada disso aparece como erro na aba Network.
 *
 * - Qualquer outro caminho é deep-link do Angular (/login, /disciplina/3) e
 *   recebe o index.html.
 *
 * no-cache (e não no-store): o navegador guarda os arquivos, mas revalida antes
 * de usar. Os bundles grandes voltam como 304 sem baixar de novo, e o index.html
 * nunca sobrevive a um deploy apontando para hashes que já não existem.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * "Parece pedido de arquivo": o último segmento tem extensão (main-A1B2.js,
     * logo-nexo-dark.png, styles.css). Rota do Angular nunca tem ponto.
     */
    private static final Pattern PEDIDO_DE_ARQUIVO = Pattern.compile(".*\\.[A-Za-z0-9]{1,8}$");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested; // arquivo real (js, css, assets, index.html…)
                        }
                        // API/WebSocket não são estáticos: deixa seguir o 404 normal
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")) {
                            return null;
                        }
                        // Arquivo pedido que não existe: 404 honesto em vez de HTML disfarçado
                        if (PEDIDO_DE_ARQUIVO.matcher(resourcePath).matches()) {
                            return null;
                        }
                        // Qualquer outra rota é deep-link do Angular → index.html
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
