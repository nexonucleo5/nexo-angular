package com.nexo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Monolito: o Spring serve o build do Angular embutido em classpath:/static/.
 *
 * Fallback de SPA — se a rota pedida não for um arquivo estático real e não for
 * /api ou /ws, devolve o index.html para o roteador do Angular assumir. É o que
 * faz deep-links e refresh (ex.: recarregar em /configuracoes) funcionarem sem 404.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested; // arquivo real (js, css, assets, index.html)
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
