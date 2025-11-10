package it.eng.dome.billing.scheduler.listener;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
public class MarkdownGenerationListener {
	
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

		if (generateApiDocs) {
			logger.info("Generating {} to display REST APIs", REST_API_MD);

			String path = contextPath + API_DOCS_PATH;
			String url = "http://localhost:" + serverPort + path.replaceAll("//+", "/");

			logger.info("GET OpenAPI call to {}", url);
			
			String json = restClient.get()
			        .uri(url)
			        .accept(MediaType.APPLICATION_JSON)
			        .retrieve()
			        .body(String.class);
			
			generateMarkdownFromJson(json, REST_API_MD);
			
			logger.info("The {} file was generated successfully", REST_API_MD);
		} else {
			logger.info("Listener unabled to generate {}. Please set 'generate-rest-apis' profile", REST_API_MD);
		}

	}

	private void generateMarkdownFromJson(String json, String outputPath) {

		try {

			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(json);

			String title = root.path("info").path("title").asText();
			String version = root.path("info").path("version").asText();
			String description = root.path("info").path("description").asText();

			// Header
			StringBuilder md = new StringBuilder();
			md.append("# ")
				.append(title).append("\n\n")
				.append("**Version:** ").append(version).append("  \n")
				.append("**Description:** ").append(description).append("  \n").append("\n\n")
				.append("## REST API Endpoints\n\n");

			// List of Endpoint
			Map<String, List<String>> tagToEndpoints = new LinkedHashMap<>();

			JsonNode paths = root.path("paths");
			paths.fieldNames().forEachRemaining(path -> {
				JsonNode methods = paths.path(path);
				methods.fieldNames().forEachRemaining(method -> {
					JsonNode operation = methods.path(method);
					String tag = operation.path("tags").get(0).asText();
					String opId = operation.path("operationId").asText();
					String line = String.format("| %s | `%s` | %s |", method.toUpperCase(), path, opId);
					tagToEndpoints.computeIfAbsent(tag, k -> new ArrayList<>()).add(line);
				});
			});

			// Display data
			for (Map.Entry<String, List<String>> entry : tagToEndpoints.entrySet()) {
				md.append("### ")
				.append(entry.getKey())
				.append("\n")
				.append("| Verb | Path | Task |\n")
				.append("|------|------|------|\n");
				
				entry.getValue().forEach(line -> md.append(line).append("\n"));
				md.append("\n");
			}

			// Write FILE.MD
			try (BufferedWriter writer = Files.newBufferedWriter(Path.of(outputPath), StandardCharsets.UTF_8)) {
				writer.write(md.toString());
			}

		} catch (Exception e) {
			logger.error("Error: {}", e.getMessage());
		}
	}
	
}

