package it.eng.dome.billing.scheduler.listener;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import it.eng.dome.brokerage.markdown.AbstractMarkdownGenerator;


@Component
public class MarkdownGenerationListener extends AbstractMarkdownGenerator {
	
	private static final Logger logger = LoggerFactory.getLogger(MarkdownGenerationListener.class);

	private final String API_DOCS_PATH = "/v3/api-docs";
	private final String REST_API_MD = "REST_APIs.md";

	@Autowired
	private RestClient restClient;

	@Value("${rest_api_docs.generate_md:false}")
	private boolean generateApiDocs;

	@Value("${server.port}")
	private int serverPort;

	@Value("${server.servlet.context-path}")
	private String contextPath;


	@EventListener(ApplicationReadyEvent.class)
	public void generateReadmeAfterStartup() {
		
		// To generate automatic REST_APIs.md doc set the generateApiDocs = true
		// Please set 'generate-rest-apis' profile (i.e. mvn spring-boot:run -Pgenerate-rest-apis)
		if (Boolean.TRUE.equals(generateApiDocs)) {
			logger.info("Generating {} to display REST APIs", REST_API_MD);

			String path = contextPath + API_DOCS_PATH;
			String url = "http://localhost:" + serverPort + path.replaceAll("//+", "/");

			logger.debug("GET JSON OpenAPI call to {}", url);
			
			try {
				// Request to JSON OpenAPI
				String json = restClient.get()
			        .uri(url)
			        .accept(MediaType.APPLICATION_JSON)
			        .retrieve()
			        .body(String.class);
				
				if (json == null || json.isBlank()) {
				    logger.warn("Received empty JSON from {}", url);
				    return;
				}

				// Get StringBuilder from JSON payload
				StringBuilder md = generateMarkdownFromJson(json);
				
				try (
				// Write FILE.MD
				BufferedWriter writer = Files.newBufferedWriter(Path.of(REST_API_MD), StandardCharsets.UTF_8)) {
					writer.write(md.toString());
				}
				
				logger.info("The {} file was generated successfully", REST_API_MD);
			} catch (Exception e) {
				logger.error("Failed to generate {}: {}", REST_API_MD, e.getMessage(), e);
			}			
			
		}
	}

}

