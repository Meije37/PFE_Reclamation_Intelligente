package com.pfe.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Convertit C:/PFE.../uploads en URI valide Spring
        String location = Paths.get(uploadPath)
                .toUri()
                .toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}