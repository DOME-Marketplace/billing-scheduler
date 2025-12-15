package it.eng.dome.billing.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import it.eng.dome.brokerage.observability.info.Info;


@Component(value = "billingFactory")
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class BillingFactory {
	
	private static final Logger log = LoggerFactory.getLogger(BillingFactory.class);
	private final String BILLING_PROXY_PATH_INFO = "/proxy/info";

	@Autowired
	RestClient restClient;
	
    @Value("${billing.billing_proxy}")
    public String billinProxy;
    

    /**
	 * Calls the Billing Proxy for getting info.
	 * 
	 * @return Info
	 * @throws Exception
	 */
	public Info getInfoBillingProxy() throws Exception {
		try {
			
			ResponseEntity<Info> response = restClient.get()
				.uri(billinProxy + BILLING_PROXY_PATH_INFO)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.toEntity(Info.class);
			
			return response.getBody();
		} catch (Exception e) {
			log.error("Exception calling invoicing service: ", e);
			throw (e);
		}
	}
}

