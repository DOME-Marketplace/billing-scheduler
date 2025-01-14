package it.eng.dome.billing.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


@Component(value = "billingFactory")
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class BillingFactory implements InitializingBean {
	
	private static final Logger log = LoggerFactory.getLogger(BillingFactory.class);
	
    @Value("${billing.invoicing_service}")
    public String invoicingService;
    
    
    @Value("${billing.billing_engine}")
    public String billinEngine;
    
    //RestTemplate restTemplate = new RestTemplate();

	@Override
	public void afterPropertiesSet() throws Exception {
		log.info("Invoicing Service is using the following invoicing endpoint prefix: " + invoicingService);	
		//someRestCall(null);
	}
/*
	public void someRestCall(String name) {
		ResponseEntity<String> res = restTemplate.getForEntity(invoicingService + "/invoicing/info", String.class);
		log.info(res.getBody());
	}
	
	public ResponseEntity<String> invoicing(String productOrder) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		HttpEntity<String> request = new HttpEntity<>(productOrder, headers);
		ResponseEntity<String> response = restTemplate.postForEntity(invoicingService + "/invoicing/applyTaxes", request, String.class);
		log.info("Response \n" + response);
		return response;
	}*/
}
