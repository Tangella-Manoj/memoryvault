package io.memoryvault.config;

import io.memoryvault.security.ChromeSessionAuthenticationFilter;
import io.memoryvault.security.JwtAuthenticationFilter;
import io.memoryvault.security.RateLimitFilter;
import io.memoryvault.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ChromeSessionAuthenticationFilter chromeSessionAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final List<String> allowedWebOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ChromeSessionAuthenticationFilter chromeSessionAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedWebOriginsCsv
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.chromeSessionAuthenticationFilter = chromeSessionAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.allowedWebOrigins = List.of(allowedWebOriginsCsv.split(","));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .headers(headers -> headers
                        .frameOptions(fo -> fo.deny())
                        .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/email/**").permitAll()
                        .requestMatchers("/api/integrations/youtube/callback").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(chromeSessionAuthenticationFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, ChromeSessionAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // The extension origin is pinned to this project's actual packed extension id
        // (derived from extension-keys/key.pem via extension/manifest.json's "key" field),
        // not a wildcard — see README "Chrome extension CORS" for how to regenerate both
        // if the signing key ever changes. The web origin(s) come from app.cors.allowed-origins
        // (comma-separated) so prod can add https://memoryvault.stacknode.dev without a code change.
        List<String> origins = new java.util.ArrayList<>(allowedWebOrigins);
        origins.add("chrome-extension://akcoccffjeibkkebonenilckdihakfjh");
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Chrome-Token",
                "X-Chrome-Session",
                "X-Forwarded-For"
        ));
        config.setExposedHeaders(List.of("Retry-After", "Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
