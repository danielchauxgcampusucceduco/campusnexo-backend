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
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS first (so even a 401 reaches the browser readable), then authentication and authorization for
 * every request, then the administration network for the admin routes; the STOMP interceptor guards the
 * live feed. Origins are patterns, so the frontend's preview deployments are accepted too.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration implements WebMvcConfigurer {

    /** Controllers receive the caller as an application {@code Requester}. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequesterArgumentResolver());
    }

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
        cors.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
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
                new AuthenticationFilter(authenticator, properties.writesPerMinute(), properties.sensorWritesPerMinute(),
                        properties.trustedProxies(), clock));
        registration.setOrder(1);
        return registration;
    }

    /** After authentication: an ADMIN outside the administration network is refused. */
    @Bean
    FilterRegistrationBean<AdminNetworkFilter> adminNetworkFilter(SecurityProperties properties) {
        FilterRegistrationBean<AdminNetworkFilter> registration = new FilterRegistrationBean<>(
                new AdminNetworkFilter(properties.adminNetworks(), properties.trustedProxies()));
        registration.setOrder(2);
        return registration;
    }
}
