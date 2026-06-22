package com.ai.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * SFTP connection settings for {@code com.ai.chatbot.ingestion.source.SftpDocumentSource}.
 * Bind from {@code ingestion.sftp.*}; supply secrets via environment variables.
 */
@ConfigurationProperties(prefix = "ingestion.sftp")
public record SftpProperties(
		String host,
		@DefaultValue("22") int port,
		String username,
		String password,
		/** Path to a private key file (optional; alternative to password auth). */
		String privateKey,
		String privateKeyPassphrase,
		@DefaultValue("/") String remoteDir,
		/** Comma-separated extensions to include, e.g. "pdf,docx". Empty = all. */
		@DefaultValue("") String fileExtensions) {

	public boolean isConfigured() {
		return host != null && !host.isBlank();
	}
}
