package com.electroshop.config;

import com.electroshop.security.JwtAuthEntryPoint;
import com.electroshop.security.JwtAuthenticationFilter;
import com.electroshop.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
    @EnableMethodSecurity
    public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
            private final RateLimitFilter rateLimitFilter;
            private final JwtAuthEntryPoint jwtAuthEntryPoint;

    @Value("${app.cors.allowed-origins}")
            private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                                                    RateLimitFilter rateLimitFilter,
                                                    JwtAuthEntryPoint jwtAuthEntryPoint) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
                this.rateLimitFilter = rateLimitFilter;
                this.jwtAuthEntryPoint = jwtAuthEntryPoint;
    }

    @Bean
            public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                        http
                                            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                            .csrf(AbstractHttpConfigurer::disable)
                                            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                            .exceptionHandling(eh -> eh.authenticationEntryPoint(jwtAuthEntryPoint))
                                            .authorizeHttpRequests(auth -> auth
                                                                                           .requestMatchers("/health").permitAll()
                                                                                           .requestMatchers("/auth/**").permitAll()
                                                                                           .requestMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
                                                                                           .requestMatchers("/uploads/**").permitAll()
                                                                                           .requestMatchers("/h2-console/**").permitAll()
                                                                                           .requestMatchers("/admin/**").hasRole("ADMIN")
                                                                                           .requestMatchers(HttpMethod.POST, "/products/**").hasRole("ADMIN")
                                                                                           .requestMatchers(HttpMethod.PUT, "/products/**").hasRole("ADMIN")
                                                                                           .requestMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")
                                                                                           .anyRequest().authenticated()
                                                                                   )
                                            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

                http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
                        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
            }

    @Bean
            public CorsConfigurationSource corsConfigurationSource() {
                        CorsConfiguration config = new CorsConfiguration();
                        config.setAllowedOriginPatterns(List.of(
                                            "https://*.vercel.app",
                                            "http://localhost:5173",
                                            "http://localhost:3000"
                                    ));
                        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                        config.setAllowedHeaders(List.of("*"));
                        config.setExposedHeaders(List.of("Authorization"));
                        config.setAllowCredentials(true);
                        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                        source.registerCorsConfiguration("/**", config);
                        return source;
            }

    @Bean
            public PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder(12);
            }

    @Bean
            public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
                        return config.getAuthenticationManager();
            }
    }
