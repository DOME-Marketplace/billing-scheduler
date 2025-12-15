package it.eng.dome.billing.scheduler.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import it.eng.dome.billing.scheduler.exception.BillingSchedulerException;
import it.eng.dome.billing.scheduler.utils.URLUtils;
import it.eng.dome.brokerage.billing.dto.BillingRequestDTO;
import it.eng.dome.brokerage.model.Invoice;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.validation.constraints.NotNull;

/**
 * This class represents a Client service to invoke, using {@link RestTemplate}, the REST APIs provided by the BillingProxy (BP) component
 */
@Service
public class BillingProxyApiClient {
	
	private static final Logger logger = LoggerFactory.getLogger(BillingProxyApiClient.class);
	
	private final String BILL_PATH = "/billing/bill";
	
	@Autowired
	private RestClient restClient;
	
	private  final String billinProxyUrl; 
	
	/**
	 * Constructor initializing the baseUrl of the DOME BillingProxy component
	 * 
	 * @param baseUrl the base URL of the DOME BillingProxy component
	 */
    public BillingProxyApiClient(@Value("${billingProxy.billingProxyService}") String baseUrl) {
    	this.billinProxyUrl = baseUrl;
    }
    
    /**
     * Invokes the BillingEngine (BE) component for the calculation of the bill for a {@link Product} in a billingPeriod (i.e., {@link TimePeriod})
     * 
     * @param billingRequestDTO A {@link BillingRequestDTO} to give in input to the REST API /billing/bill of the BE
     * @param endpoint The endpoint of the BE. If null the default DOME BE endpoint will be considered
     * @return A list of {@link Invoice} with the calculate bills
     * @throws BillingSchedulerException if an {@link Error} occurs during the invocation of the REST API /billing/bill
     */
    public List<Invoice> billingBill(@NotNull String productId, @NotNull TimePeriod billPeriod) throws BillingSchedulerException{
    	
        BillingRequestDTO billingRequestDTO=new BillingRequestDTO(productId, billPeriod, null);	
        
    	String url =URLUtils.buildUrl(billinProxyUrl, BILL_PATH);
		logger.debug("Invocation of BillingProxy API: {}", url);
		
		ResponseEntity<List<Invoice>> response = restClient.post()
		        .uri(url)
		        .contentType(MediaType.APPLICATION_JSON)
		        .body(billingRequestDTO)
		        .retrieve()
		        .toEntity(new ParameterizedTypeReference<List<Invoice>>() {});
			
		if (response != null && response.getBody() != null) {
			return response.getBody();
		}else {
			throw new BillingSchedulerException("Error in the invocation of the BillingProxy API: " + url + " - Response body is null");
		}
    }

}
