package it.eng.dome.billing.scheduler.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
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
import it.eng.dome.tmforum.tmf678.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.api.AppliedCustomerBillingRateApi;
import it.eng.dome.tmforum.tmf678.v4.api.CustomerBillApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(BillingService.class);
	
	@Autowired
	private TmfApiFactory tmfApiFactory;
	
	private ProductApi productApi;
	private AppliedCustomerBillingRateApi appliedCustomerBillingRate;
	
	private CustomerBillApi customerBill;
	
	@Autowired
	protected BillingFactory billing;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		productApi = new ProductApi(tmfApiFactory.getTMF637ProductInventoryApiClient());	

		appliedCustomerBillingRate = new AppliedCustomerBillingRateApi(tmfApiFactory.getTMF678ProductInventoryApiClient());
		customerBill = new CustomerBillApi(tmfApiFactory.getTMF678ProductInventoryApiClient()); 
	}
	
	public String calculateBuilling() throws Exception {
		
		// 1) retrieve all products
		logger.info("Retrieve all products");
		List<Product> products = productApi.listProduct(null, null, null);
			
		logger.debug("Number of Product found: {} ", products.size());

		//TODO - how to improve filtering of product
		
		for (Product product : products) {			
			logger.debug("Analyze productId: " + product.getId() + " with status: " + product.getStatus());
			
			// Condition 1 -> status=active && priceType=recurring
			if (product.getStatus() == ProductStatusType.ACTIVE) {
				
				List<ProductPrice> pprices = product.getProductPrice();
				
				// check if priceType = recurring from productPrice
				for (ProductPrice pprice : pprices) {
					logger.debug("Check priceType in ProductPrice: " + pprice.getName() + " - " + pprice.getPriceType());
					
					//TODO verify if it must use recurring-prepaid and recurring-postpaid
					if ("recurring".equals(pprice.getPriceType().toLowerCase())) {
												
						// Condition 2 -> verify that the bill is not expired and is already calculated
						if (!isExpired() && !isBilled()) { //TODO missing condition 2
							
							// start bill process
							bill(product);
							
						}else {
							logger.info("Bill for productId {} has already been billed or expired", product.getId());
						}
						
						//TODO we are in a loop - I have to check into all productPrices the priceType or I can exit from loop after the first "recurring" found
						// use break if you want to skip after match the priceType=recurring  
						//break;
					
					}else {
						logger.debug("No bill for productId {} because priceType = {} is not recurring", product.getId(), pprice.getPriceType());
					}
					
					
				}
			}else {
				logger.debug("Bill skipped for productId {} because status = {} is not active", product.getId(), product.getStatus());
			}
		}	
		
		// TEST customerBill
		List<CustomerBill> customers = customerBill.listCustomerBill(null, null, null);
		logger.debug("number of customerBill found: " + customers.size());
		for (CustomerBill customerBill : customers) {
			logger.debug("customerId: " + customerBill.getId());
		}
		return null;
	}
	
	private boolean isBilled() {
		// TODO implement the logic - check if exist another AppliedCustomerBillingRate
		try {
			List<AppliedCustomerBillingRate> applied = appliedCustomerBillingRate.listAppliedCustomerBillingRate(null, null, null);
			logger.debug("Number of AppliedCustomerBillingRate found: {} ", applied.size());
			for (AppliedCustomerBillingRate appliedCustomerBillingRate : applied) {
				//appliedCustomerBillingRate.getDate()
				logger.info("AppliedCustomerBillingRate:");
				
				//TODO verify type=recurringCharge ???
				//TODO verify periodCoverage
				if ((appliedCustomerBillingRate.getType().startsWith("recurring")) && isPeriodCoverage(appliedCustomerBillingRate.getPeriodCoverage())) {
					logger.info(appliedCustomerBillingRate.toJson());
					return false;
				}
			}	
			return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	private boolean isPeriodCoverage(TimePeriod tp) {
		OffsetDateTime currentDateTime = OffsetDateTime.now();
		boolean isWithinRange = (currentDateTime.isEqual(tp.getStartDateTime()) || currentDateTime.isAfter(tp.getStartDateTime())) &&
		   (currentDateTime.isEqual(tp.getEndDateTime()) || currentDateTime.isBefore(tp.getEndDateTime()));
		
		logger.info("Period Coverage check: {}", isWithinRange);
		 
		return isWithinRange;
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
		String orderJson = getProductOrderJson();
		ResponseEntity<String> response = invoicing(orderJson);	
		logger.info("Status code {}", response.getStatusCode());
		logger.info("Order with Tax \n {} ", response.getBody() );
		
		
		// save the bill (AppliedCustomerBillRate) in TMForum
		logger.info("Save AppliedCustomerBillRate in TMForum");
		String acbrJson = getAppliedCustomerBillingrateJson();
		System.out.println("Payload for AppliedCustomerBillRate:\n" + acbrJson);
		
		try {
			AppliedCustomerBillingRateCreate acbr = AppliedCustomerBillingRateCreate.fromJson(acbrJson);
			appliedCustomerBillingRate.createAppliedCustomerBillingRate(acbr);
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			logger.info("AppliedCustomerBillingRate not saved!");
			logger.error(e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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

	private String getProductOrderJson() {
		String file = "src/main/resources/productorder.json";
		try {
			return new String(Files.readAllBytes(Paths.get(file)));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	private String getAppliedCustomerBillingrateJson() {
		String file = "src/main/resources/appliedcustomerbillingrate.json";
		try {
			return new String(Files.readAllBytes(Paths.get(file)));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
}
