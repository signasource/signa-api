package com.signasource.signa_api.config;

import com.signasource.signa_api.auth.service.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Profile("!local")
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Missing/invalid/expired bearer token => 401 (clients refresh on 401); a valid
                // token
                // without the required role still gets 403 from the access-denied handler.
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/auth/**")
                                        .permitAll()
                                        .requestMatchers("/users/username-availability")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/waitlist")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/signs")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/organizations/invite-codes/redeem")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/organizations/me")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.POST, "/organizations")
                                        .hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/organizations/*/admins")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/organizations/*/admin-invitations")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST, "/organizations/*/courses")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.DELETE, "/organizations/*/courses/*")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/organizations/**")
                                        .hasAnyRole("ADMIN", "ORG_ADMIN")
                                        .requestMatchers("/actuator/health", "/actuator/info")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/signs/*/animation")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Profile("local")
    @Bean
    public SecurityFilterChain localSecurityFilterChain(
            HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
