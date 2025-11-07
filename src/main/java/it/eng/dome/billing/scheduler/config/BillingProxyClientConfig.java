package it.eng.dome.billing.scheduler.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

//@Configuration
//@Qualifier("billingProxyClient")
public class BillingProxyClientConfig {
	
	//@Value("${billing.billing_proxy}")
    public String billingProxyUrl;

	//@Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl(billingProxyUrl)
                .build();
    }
}
