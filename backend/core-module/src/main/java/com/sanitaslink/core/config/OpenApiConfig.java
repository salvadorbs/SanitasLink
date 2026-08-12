package com.sanitaslink.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI definition with bearer-token security scheme. */
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
                        .bearerFormat("JWT")));
  }
}
