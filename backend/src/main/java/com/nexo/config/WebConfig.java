package com.nexo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Monolito: o Spring serve o build do Angular embutido em classpath:/static/.
 *
 * São dois handlers de propósito, e a separação é o que evita a tela branca:
 *
 * 1) Os artefatos do build (main-HASH.js, chunk-HASH.js, styles-HASH.css, assets)
 *    têm o hash no nome, então podem ser cacheados por um ano — e, quando não
 *    existem, devolvem 404 de verdade. Antes tudo caía no fallback abaixo: um
 *    index.html velho no cache do navegador pedia um hash que já não existia, o
 *    servidor respondia 200 com o index.html, e o navegador recebia "text/html"
 *    onde esperava JavaScript/CSS. Resultado: script que não executa e folha de
 *    estilo ignorada — sem nenhum 404 visível na aba Network para denunciar.
 *
 * 2) O resto é deep-link do Angular (/login, /dashboards, /disciplina/3) e recebe
 *    o index.html — com no-store, porque ele é justamente quem aponta para os
 *    hashes do deploy atual e não pode sobreviver ao próximo deploy.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ── 1) Artefatos com hash: cache longo, 404 honesto quando não existem ──
        // O angular.json usa outputPath.browser = "", então os bundles ficam na raiz.
        registry.addResourceHandler(
                        "/*.js", "/*.css", "/*.map", "/*.js.map", "/*.css.map",
                        "/assets/**", "/media/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // ── 2) Tudo o mais: arquivo real, ou index.html para o roteador do Angular ──
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested; // arquivo real (index.html, favicon, robots.txt…)
                        }
                        // API/WebSocket não são estáticos: deixa seguir o 404 normal
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")) {
                            return null;
                        }
                        // Qualquer outra rota é deep-link do Angular → index.html
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
