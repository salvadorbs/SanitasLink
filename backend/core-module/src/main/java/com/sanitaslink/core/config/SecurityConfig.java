package com.sanitaslink.core.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.sanitaslink.core.security.JwtAuthenticationConverter;
import com.sanitaslink.core.security.PermissionEnrichmentFilter;
import com.sanitaslink.core.security.PermissionResolver;
import com.sanitaslink.core.security.ProblemDetailAccessDeniedHandler;
import com.sanitaslink.core.security.ProblemDetailAuthenticationEntryPoint;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/** Spring Security configuration: stateless JWT resource server with method security. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({
  JwtProperties.class,
  TokenProperties.class,
  CorsProperties.class,
  LoginRateLimitProperties.class,
  NotificationProperties.class
})
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtConverter,
      PermissionEnrichmentFilter enrichmentFilter,
      ProblemDetailAuthenticationEntryPoint entryPoint,
      ProblemDetailAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/invitations/accept",
                        "/api/v1/auth/password-reset/request",
                        "/api/v1/auth/password-reset/confirm")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                    .authenticationEntryPoint(entryPoint))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(enrichmentFilter, AuthorizationFilter.class);
    return http.build();
  }

  @Bean
  public JwtEncoder jwtEncoder(JwtProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey(properties)));
  }

  @Bean
  public JwtDecoder jwtDecoder(JwtProperties properties) {
    return NimbusJwtDecoder.withSecretKey(secretKey(properties))
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.getAllowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PermissionEnrichmentFilter permissionEnrichmentFilter(
      PermissionResolver resolver, ObjectMapper objectMapper) {
    return new PermissionEnrichmentFilter(resolver, objectMapper);
  }

  @Bean
  public ProblemDetailAuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
    return new ProblemDetailAuthenticationEntryPoint(objectMapper);
  }

  @Bean
  public ProblemDetailAccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
    return new ProblemDetailAccessDeniedHandler(objectMapper);
  }

  @Bean
  public FilterRegistrationBean<PermissionEnrichmentFilter> disablePermissionFilterAutoRegistration(
      PermissionEnrichmentFilter filter) {
    FilterRegistrationBean<PermissionEnrichmentFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  private SecretKey secretKey(JwtProperties properties) {
    String secret = properties.getSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "sanitaslink.security.jwt.secret must be configured (set SANITASLINK_JWT_SECRET)");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(secret);
    } catch (IllegalArgumentException e) {
      bytes = secret.getBytes(StandardCharsets.UTF_8);
    }
    if (bytes.length < 32) {
      throw new IllegalStateException(
          "sanitaslink.security.jwt.secret must decode to at least 32 bytes (256 bits)");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
}
