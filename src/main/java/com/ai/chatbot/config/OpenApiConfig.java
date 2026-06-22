package com.ai.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * OpenAPI metadata for the generated spec and the Swagger UI.
 *
 * <p>springdoc auto-discovers the {@code @RestController}s and serves:
 * <ul>
 *   <li>Swagger UI at {@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON at {@code /v3/api-docs}</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI aiChatbotOpenApi() {
		return new OpenAPI().info(new Info()
				.title("ai-chatbot API")
				.version("0.0.1")
				.description("Local, self-hosted RAG service (Spring AI + pgvector + Ollama) "
						+ "with a pluggable document-ingestion pipeline (URL, SFTP, Notion)."));
	}
}
