package br.com.fiap.agendafit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // necessário apenas quando o app roda no navegador (Expo Web)
        registry.addMapping("/**").allowedOrigins("*").allowedMethods("*").exposedHeaders("Location");
    }
}
