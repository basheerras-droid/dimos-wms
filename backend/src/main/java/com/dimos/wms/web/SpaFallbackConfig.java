package com.dimos.wms.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React app from classpath:/static. For page addresses like /login or /inventory (which exist only inside
 * the browser app) it returns index.html, so a reload or a bookmark works. API and actuator paths are never rewritten.
 */
@Configuration
class SpaFallbackConfig implements WebMvcConfigurer {

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
                            return requested;
                        }
                        boolean isApi = resourcePath.startsWith("api/") || resourcePath.startsWith("actuator");
                        boolean isFile = resourcePath.contains("."); // missing .js/.png etc. should be a real 404
                        return (isApi || isFile) ? null : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
