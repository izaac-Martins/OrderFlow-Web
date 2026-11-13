package com.orderflow.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configura o Swagger UI para expor diretamente o contrato-fonte {@code contratos/openapi.yaml}
 * do monorepo OrderFlow (abordagem Contract-First).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlow Platform API")
                        .version("1.0.0")
                        .description("Contrato-fonte OpenAPI 3.1 do Ecossistema OrderFlow (Cafeteria Comercial)"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @RestController
    static class OpenApiContractController {

        private final Path contractPath;

        OpenApiContractController(@Value("${orderflow.openapi.contract-path:../contratos/openapi.yaml}") String contractPath) {
            this.contractPath = Path.of(contractPath).toAbsolutePath().normalize();
        }

        @GetMapping(value = "/openapi.yaml", produces = "application/vnd.oai.openapi")
        public ResponseEntity<Resource> serveContractYaml() {
            if (Files.exists(contractPath)) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.oai.openapi;charset=UTF-8"))
                        .body(new FileSystemResource(contractPath));
            }
            return ResponseEntity.notFound().build();
        }
    }
}
