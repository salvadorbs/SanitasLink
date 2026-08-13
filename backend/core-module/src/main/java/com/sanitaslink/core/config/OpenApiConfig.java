package com.sanitaslink.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI definition with bearer-token and refresh-cookie security schemes. The cookie scheme is
 * used by the auth endpoints that authenticate through the HttpOnly `sl_refresh` cookie (login,
 * refresh, logout); every other endpoint requires the bearer JWT.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI sanitasLinkOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SanitasLink API")
                .description(
                    "Identity, office (tenant) and authorization APIs. "
                        + "The tenant is always derived from the authenticated JWT; "
                        + "no X-Office-Id header is used or accepted.")
                .version("v1"))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(
                    "cookieAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("sl_refresh")
                        .description(
                            "HttpOnly refresh-token cookie scoped to /api/v1/auth. The raw "
                                + "refresh token never appears in response bodies; it is rotated "
                                + "on every refresh and issued/cleared through Set-Cookie. "
                                + "Secure and SameSite=Strict outside the local dev profile.")));
  }
}
