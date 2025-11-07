package it.eng.dome.billing.scheduler.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

//@Component
public class BillingProxyApiClient {
	
	private final WebClient webClient=null;

   // public BillingProxyApiClient(@Qualifier("billingProxyClient") WebClient webClient) {
    //    this.webClient = webClient;
    //}

}
