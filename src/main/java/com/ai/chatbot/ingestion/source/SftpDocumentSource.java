package com.ai.chatbot.ingestion.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.stereotype.Component;

import com.ai.chatbot.config.SftpProperties;
import com.ai.chatbot.ingestion.DocumentSource;
import com.ai.chatbot.ingestion.IngestionService;
import com.ai.chatbot.ingestion.SourceRequest;

/**
 * Scaffold connector: lists files in a remote SFTP directory and parses each
 * (PDF, DOC, DOCX, ...) with Apache Tika. Wired to the {@link DocumentSource}
 * contract; requires {@code ingestion.sftp.*} configuration to run. Verify
 * against a real SFTP server before production use.
 *
 * <p>Request params (optional, override config): {@code "remoteDir"}.
 */
@Component
public class SftpDocumentSource implements DocumentSource {

	private final SftpProperties properties;

	public SftpDocumentSource(SftpProperties properties) {
		this.properties = properties;
	}

	@Override
	public String type() {
		return "sftp";
	}

	@Override
	public List<Document> fetch(SourceRequest request) {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("SFTP is not configured. Set ingestion.sftp.host (and credentials).");
		}
		String remoteDir = request.getString("remoteDir", properties.remoteDir());
		List<Document> documents = new ArrayList<>();

		Session<?> session = sessionFactory().getSession();
		try {
			for (String name : session.listNames(remoteDir)) {
				if (!matchesExtension(name)) {
					continue;
				}
				String path = remoteDir.endsWith("/") ? remoteDir + name : remoteDir + "/" + name;
				byte[] bytes;
				try (InputStream in = session.readRaw(path)) {
					bytes = in.readAllBytes();
				}
				session.finalizeRaw();

				for (Document parsed : new TikaDocumentReader(new ByteArrayResource(bytes)).read()) {
					Map<String, Object> metadata = new HashMap<>(parsed.getMetadata());
					metadata.put(IngestionService.SOURCE_URI, path);
					metadata.put(IngestionService.TITLE, name);
					documents.add(new Document(parsed.getText(), metadata));
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("SFTP fetch failed for " + remoteDir, ex);
		}
		finally {
			session.close();
		}
		return documents;
	}

	private boolean matchesExtension(String name) {
		String extensions = properties.fileExtensions();
		if (extensions == null || extensions.isBlank()) {
			return true;
		}
		String lower = name.toLowerCase();
		for (String ext : extensions.split(",")) {
			if (lower.endsWith("." + ext.trim().toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private DefaultSftpSessionFactory sessionFactory() {
		DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
		factory.setHost(properties.host());
		factory.setPort(properties.port());
		factory.setUser(properties.username());
		if (properties.password() != null && !properties.password().isBlank()) {
			factory.setPassword(properties.password());
		}
		if (properties.privateKey() != null && !properties.privateKey().isBlank()) {
			factory.setPrivateKey(new FileSystemResource(properties.privateKey()));
			if (properties.privateKeyPassphrase() != null) {
				factory.setPrivateKeyPassphrase(properties.privateKeyPassphrase());
			}
		}
		// For demos; in production pin host keys via setKnownHostsResource instead.
		factory.setAllowUnknownKeys(true);
		return factory;
	}
}
