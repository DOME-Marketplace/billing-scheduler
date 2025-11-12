package it.eng.dome.billing.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class AppConfig {

	// register RestClient for serializing
	@Bean
	public RestClient restClient(ObjectMapper objectMapper) {

		MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

		return RestClient.builder().messageConverters(converters -> {
			converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
			converters.add(jacksonConverter);
		}).build();
	}
	
	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
