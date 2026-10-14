package com.apexgrid.engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration.
 *
 * The React SPA is hosted on Netlify (a different origin from this Render
 * service), so the browser performs cross-origin requests against the REST
 * API. Without these headers every preflight/subsequent call is blocked.
 *
 * Origins are intentionally permissive for this public demo engine; tighten
 * `allowedOriginPatterns` to specific Netlify domains for production use.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
