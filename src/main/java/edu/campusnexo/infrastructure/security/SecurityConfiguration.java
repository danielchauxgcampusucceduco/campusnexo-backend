package edu.campusnexo.infrastructure.security;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS first (so even a 401 reaches the browser readable), then authentication and authorization for
 * every request; the STOMP interceptor guards the live feed.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

    @Bean
    Authenticator authenticator(SecurityProperties properties, Clock clock) {
        return new Authenticator(properties, clock);
    }

    @Bean
    StompAuthenticationInterceptor stompAuthenticationInterceptor(Authenticator authenticator) {
        return new StompAuthenticationInterceptor(authenticator);
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilter(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", Authenticator.AUTHORIZATION_HEADER, Authenticator.API_KEY_HEADER));
        cors.setExposedHeaders(List.of("Location", "X-Next-Cursor", "Retry-After"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(0);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AuthenticationFilter> authenticationFilter(Authenticator authenticator,
                                                                      SecurityProperties properties, Clock clock) {
        FilterRegistrationBean<AuthenticationFilter> registration = new FilterRegistrationBean<>(
                new AuthenticationFilter(authenticator, properties.writesPerMinute(), clock));
        registration.setOrder(1);
        return registration;
    }
}
