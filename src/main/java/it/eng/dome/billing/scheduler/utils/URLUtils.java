package it.eng.dome.billing.scheduler.utils;

import org.springframework.web.util.UriComponentsBuilder;

import jakarta.validation.constraints.NotNull;

public class URLUtils {
	
	public static String buildUrl(@NotNull String endpoint, @NotNull String path){
		
		String sanitizedEndpoint = endpoint.replaceAll("/+$", "");
		String url = UriComponentsBuilder
		        .fromUriString(sanitizedEndpoint)
		        .path(path.startsWith("/") ? path : "/" + path)
		        .toUriString();
		
		return url;
	}

}
