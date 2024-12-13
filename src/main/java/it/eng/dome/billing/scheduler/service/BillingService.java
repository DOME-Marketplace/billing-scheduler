package it.eng.dome.billing.scheduler.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import it.eng.dome.billing.scheduler.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf637.v4.api.ProductApi;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;
import it.eng.dome.tmforum.tmf678.v4.api.AppliedCustomerBillingRateApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;

@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(BillingService.class);
	
	@Autowired
	private TmfApiFactory tmfApiFactory;
	
	private ProductApi productApi;
	private AppliedCustomerBillingRateApi appliedCustomerBillingRate;
	
	@Autowired
	protected BillingFactory billing;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		productApi = new ProductApi(tmfApiFactory.getTMF637ProductInventoryApiClient());	
		appliedCustomerBillingRate = new AppliedCustomerBillingRateApi(tmfApiFactory.getTMF678ProductInventoryApiClient());
	}
	
	public String calculateBuilling() throws Exception {
		
		// 1) retrieve all products
		logger.info("Retrieve all products");
		List<Product> products = productApi.listProduct(null, null, null);
			
		logger.debug("Number of Product found: {} ", products.size());

		//TODO - how to filter
		
		for (Product product : products) {			
			logger.debug("Analyze productId: " + product.getId() + " with status: " + product.getStatus());
			
			// Condition 1 -> status=active && priceType=recurring
			if (product.getStatus() == ProductStatusType.ACTIVE) {
				
				List<ProductPrice> pprices = product.getProductPrice();
				
				for (ProductPrice pprice : pprices) {
					logger.debug("Check priceType in ProductPrice: " + pprice.getName() + " - " + pprice.getPriceType());
					
					if ("recurring".equals(pprice.getPriceType().toLowerCase())) {
												
						// Condition 2 -> verify that the bill is not expired and is already calculated
						if (!isExpired() && !isBilled()) { //TODO missing condition 2
							
							bill(product);
							
						}else {
							logger.info("Bill for productId {} has already been billed", product.getId());
						}
					
					}else {
						logger.debug("No bill for productId {} because priceType = {} is not recurring", product.getId(), pprice.getPriceType());
					}
				}
			}else {
				logger.debug("Bill skipped for productId {} because status = {} is not active", product.getId(), product.getStatus());
			}
		}			
		return null;
	}
	
	private boolean isBilled() {		
		try {
			List<AppliedCustomerBillingRate> applied = appliedCustomerBillingRate.listAppliedCustomerBillingRate(null, null, null);
			logger.debug("Number of AppliedCustomerBillingRate found: {} ", applied.size());
			for (AppliedCustomerBillingRate appliedCustomerBillingRate : applied) {
				//appliedCustomerBillingRate.getDate()
				logger.info(appliedCustomerBillingRate.getId());
				return true;
			}	
			return false;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	private boolean isExpired() {
		// TODO add logic - check date
		return false;
	}
	
	private void bill(Product product) {
		logger.info("Start bill process for productId: " + product.getId());
		// Invocazione del billing in modalità consuntiva (input Product -> output Bill) 
		
		// Invocazione dell'invoicing in modalità consuntiva (input Bill -> output Bill) 
		
		//TODO get the product order ini other way!!!
		String orderJson = getJson();
		ResponseEntity<String> response = invoicing(orderJson);	
		logger.info("Status code {}", response.getStatusCode());
		logger.info("Order with Tax \n {} ", response.getBody() );
		
		// save the bill (AppliedCustomerBillRate) in TMForum
		logger.info("Save AppliedCustomerBillRate in TMForum");
	}

	/*
  	Verificare quando è il momento di generare il Bill per un Product (check a partire dalla lista dei Product)-> Tipi di Product che si prende in considerazione -> “recurring” (escludiamo il “pay per use”) 

   	Check se il Bill per il Product in questione è già stato calcolato e salvato in tmforum 

	Invocazione del billing in modalità consuntiva (input Product -> output Bill) 
	
	Invocazione dell’invoicing in modalità consuntiva (input Bill -> output Bill) 
	
	Persistenza del Bill su tmforum 
	 */	
	
	RestTemplate restTemplate = new RestTemplate();
	
	private ResponseEntity<String> invoicing(String productOrder) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(productOrder, headers);
		return restTemplate.postForEntity(billing.invoicingService + "/invoicing/applyTaxes", request, String.class);
	}
	
	/* TO DELETE */

	private String getJson() {
		String file = "src/main/resources/productorder.json";
		try {
			return new String(Files.readAllBytes(Paths.get(file)));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
}
