package it.eng.dome.billing.scheduler.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import it.eng.dome.tmforum.tmf620.v4.api.ProductOfferingPriceApi;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.api.ProductApi;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;
import it.eng.dome.tmforum.tmf678.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.JSON;
import it.eng.dome.tmforum.tmf678.v4.api.AppliedCustomerBillingRateApi;
import it.eng.dome.tmforum.tmf678.v4.api.CustomerBillApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;
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
	private ProductOfferingPriceApi productOfferingPrice;
	
	@Autowired
	protected BillingFactory billing;
	
	private final static String PREFIX_KEY = "period-";
	
	RestTemplate restTemplate = new RestTemplate();
			
	@Override
	public void afterPropertiesSet() throws Exception {
		productApi = new ProductApi(tmfApiFactory.getTMF637ProductInventoryApiClient());	
		productOfferingPrice = new ProductOfferingPriceApi(tmfApiFactory.getTMF620CatalogApiClient());

		appliedCustomerBillingRate = new AppliedCustomerBillingRateApi(tmfApiFactory.getTMF678ProductInventoryApiClient());
		customerBill = new CustomerBillApi(tmfApiFactory.getTMF678ProductInventoryApiClient()); 
	}
	
public void calculateBuilling() throws Exception {
		
		// 1) retrieve all products
		logger.info("Retrieve all products");
		List<Product> products = productApi.listProduct(null, null, null);
			
		logger.debug("Number of Product found: {} ", products.size());

		//TODO - how to improve filtering of product
		
		OffsetDateTime now = OffsetDateTime.parse("2024-08-24T01:04:30.983Z"); // OffsetDateTime.now();
		int count = 0;
		
		for (Product product : products) {			
			logger.debug("----------------------------------------------------------");
			logger.debug("Product item # {} - {}", ++count, product.getName());
			//logger.debug("product - " + product.getName());
			logger.debug("Analyze productId: " + product.getId() + " with status: " + product.getStatus());
			
			// Condition 1 -> status=active && priceType=recurring
			if (product.getStatus() == ProductStatusType.ACTIVE) {
				
				List<ProductPrice> pprices = product.getProductPrice();
				
				Map<String, List<TimePeriod>> timePeriods = new HashMap<>();
				Map<String, List<ProductPrice>> productPrices = new HashMap<>();
				
				// check if priceType = recurring from productPrice
				for (ProductPrice pprice : pprices) {					
					
					//TODO verify if it must use recurring-prepaid and recurring-postpaid
					if ("recurring".equals(pprice.getPriceType().toLowerCase())) {
												
						String recurringPeriod = null;
						
						if (pprice.getProductOfferingPrice() != null) {// check on ProductOfferingPrice 								
							// GET recurringChargePeriodType + recurringChargePeriodLength
							logger.debug("Use Case - ProductOfferingPrice for product: " + product.getId());	
							recurringPeriod = getRecurringPeriod( pprice.getProductOfferingPrice().getId());
							logger.debug("Recurring period: " + recurringPeriod);

						}else if (pprice.getRecurringChargePeriod() != null) {// check on RecurringChargePeriod									
							logger.debug("Use Case - RecurringChargePeriod for product: " + product.getId());
							recurringPeriod = pprice.getRecurringChargePeriod();
							logger.debug("Recurring period: " + recurringPeriod);								
						}
						
						if (recurringPeriod != null && product.getStartDate() != null) {
							OffsetDateTime nextBillingTime = getNextBillingTime(product.getStartDate(), now, recurringPeriod);
							OffsetDateTime previousBillingTime = getPreviousBillingTime(nextBillingTime, recurringPeriod);
							
							if (nextBillingTime != null) {
								logger.debug("StartDate: " + product.getStartDate());
								logger.debug("recurring: " + recurringPeriod);
								logger.debug("NextDate: " + nextBillingTime);
								logger.debug("PreviuosDate: " + previousBillingTime);						
								logger.debug("CurrentDate: " + now);
								
								long days = ChronoUnit.DAYS.between(now, nextBillingTime);
								//System.out.println(">>>days missing for billing: " + days);
								//System.out.println("diff: " + ChronoUnit.DAYS.between(previousBillingTime, nextBillingTime));
								String keyPeriod = PREFIX_KEY + ChronoUnit.DAYS.between(previousBillingTime, nextBillingTime);
								logger.debug("keyPeriod: " + keyPeriod);
								if (days == 0) {
									TimePeriod tp = new TimePeriod();
									tp.setStartDateTime(previousBillingTime);
									tp.setEndDateTime(nextBillingTime);
									
									timePeriods.put(keyPeriod, new ArrayList<>(Arrays.asList(tp)));
									productPrices.computeIfAbsent(keyPeriod, k -> new ArrayList<>()).add(pprice);
								}
							}
						}else {
							logger.debug("No recurringPeriod found or product.startDate valid");
						}
					
					}else {
						logger.debug("No bill for productId {} because priceType = {} is not recurring", product.getId(), pprice.getPriceType());
					}
				}
				
				logger.debug("End scanning of ProductPrice");
				logger.info("Number of item for billing: {}", productPrices.size());
				for (Map.Entry<String, List<ProductPrice>> entry : productPrices.entrySet()) {
					
					String key = entry.getKey();
					logger.debug("key: " + key);

					TimePeriod tp = timePeriods.get(key).get(0);
					if (timePeriods.get(key).size() > 0) {
						logger.debug("TimePeriodo - startDate: " + tp.getStartDateTime() + " - endDate: " + tp.getEndDateTime());
						List<ProductPrice> pps = entry.getValue();
						for (ProductPrice pp : pps) {
							logger.debug(pp.getName() + " || " + pp.getPriceType());
						}						
						
						//TODO verify if the billing is already done
						if (!isAlreadyBilled(product, tp, pps)) {
							logger.debug("Apply billing - AppliedCustomerBillingRate: " + product.getId());
							
							//TODO invoke billing-engine
							String applied = getAppliedCustomerBillingrateJson();
							
							//TODO invoke invoicing-service
							ResponseEntity<String> invoicing = invoicing(applied);
							logger.info("Status code {}", invoicing.getStatusCode());
							//logger.info("AppliedCustomerBillingRate with Tax \n {} ", invoicing.getBody());
							String appliedCustomerBillingRatesJson = invoicing.getBody();
							//logger.info("PAYLOAD \n{}", appliedCustomerBillingRatesJson);
							
							//TODO save AppliedCustomerBillingRate[] with taxes in TMForum
							List<String> ids = bill(appliedCustomerBillingRatesJson);
							logger.info("Saved #{} AppliedCustomerBillingRate", ids.size());
							logger.debug("AppliedCustomerBillingRate ids: {}", ids);
						}else {
							logger.debug("Billing already done for productId: {}", product.getId());
						}
						
					}
				}
			}else {
				logger.debug("Bill skipped for productId {} because status = {} is not active", product.getId(), product.getStatus());
			}
		}	
	}


	private String getRecurringPeriod(String id) {
		try {
			ProductOfferingPrice pop = productOfferingPrice.retrieveProductOfferingPrice(id, null);
			logger.debug("RecurringChargePeriodLength: {}", pop.getRecurringChargePeriodLength());
			logger.debug("getRecurringChargePeriodType: {}", pop.getRecurringChargePeriodType());
			return pop.getRecurringChargePeriodLength() + " " + pop.getRecurringChargePeriodType();
		} catch (it.eng.dome.tmforum.tmf620.v4.ApiException e) {
			logger.error(e.getMessage());
			return null;
		}
	}
	
	private OffsetDateTime getNextBillingTime(OffsetDateTime t, OffsetDateTime now, String s) {
		String[] data = s.split("\\s+");
		if (data.length == 2) {
			if (data[0].matches("-?\\d+")) { // if data[0] is a number
				return nextBillingTime(t, now, Integer.parseInt(data[0]),  data[1]);
			}
		}else if (data.length == 1) {
			return nextBillingTime(t, now, 1, data[0]);
		}		
		return null;
	}
	
	private OffsetDateTime getPreviousBillingTime(OffsetDateTime t, String s) {
		String[] data = s.split("\\s+");
		if (data.length == 2) {
			if (data[0].matches("-?\\d+")) { // if data[0] is a number
				return getPreviusBilling(t, Integer.parseInt(data[0]),  data[1]);
			}
		}else if (data.length == 1) {
			return getPreviusBilling(t, 1, data[0]);
		}		
		return null;
	}
	
	private OffsetDateTime nextBillingTime(OffsetDateTime time, OffsetDateTime now, int number, String unit) {
		if ((time.toLocalDate().equals(now.toLocalDate()) || (time.isAfter(now)))) {
			return time;
		}else {
		
			switch (unit) {
				case "day":
		        case "days":
		        case "daily":
		            return nextBillingTime(time.plusDays(number), now, number, unit);
		        case "week":
		        case "weeks":
		        case "weekly":
		            return nextBillingTime(time.plusWeeks(number), now, number, unit);
		        case "month":
		        case "months":
		        case "monthly":
		            return nextBillingTime(time.plusMonths(number), now, number, unit);
		        case "year":
		        case "years":
		            return nextBillingTime(time.plusYears(number), now, number, unit);
		        default:
		            return null;
		    }
		}
	}
	
	private OffsetDateTime getPreviusBilling(OffsetDateTime time, int number, String unit) {
		switch (unit) {
			case "day":
	        case "days":
	        case "daily":
	            return time.minusDays(number);
	        case "week":
	        case "weeks":
	        case "weekly":
	            return time.minusWeeks(number);
	        case "month":
	        case "months":
	        case "monthly":
	            return time.minusMonths(number);
	        case "year":
	        case "years":
	            return time.minusYears(number); 
	        default:
	            return null;
	    }
	}
	
	private boolean isAlreadyBilled(Product product, TimePeriod tp, List<ProductPrice> productPrices) {
		logger.info("Verifying product is billed ...");
		boolean isBilled = false;
		try {
			List<AppliedCustomerBillingRate> billed = appliedCustomerBillingRate.listAppliedCustomerBillingRate("product,periodCoverage", null, null);
			logger.debug("Number of AppliedCustomerBillingRate found: {} ", billed.size());
			
			for (AppliedCustomerBillingRate bill : billed) {
				String id = bill.getProduct().getId();
				logger.info("ProductId to verify: {}", product.getId());

				if(id.equals(product.getId())) {
					logger.debug("Step 1 - found AppliedCustomerBillingRate with the same ProductId");
					if (tp.equals(bill.getPeriodCoverage())) { 
						logger.debug("Step 2 - found PeriodCoverage with the same TimePeriod");
						
						//TODO check productPrices
						
						logger.info("Found product already billed");
						return true;
					}else {
						logger.debug("Stopped verifying: different TimePeriod");
					}
				}else {
					logger.debug("Stopped verifying: different ProductId");
				}
			}
		} catch (ApiException e) {
			logger.error(e.getMessage());
			return isBilled;
		}
		logger.info("Product needs to be billed");
		return isBilled;
	}
	
	private ResponseEntity<String> invoicing(String appliedCustomerBillingRates) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(appliedCustomerBillingRates, headers);
		return restTemplate.postForEntity(billing.invoicingService + "/invoicing/applyTaxes", request, String.class);
	}
	
	private List<String> bill(String appliedCustomerBillRates) {
		logger.info("Starting the billing process");
		
		List<String> ids = new ArrayList<String>();
		
		try {
			AppliedCustomerBillingRate[] bills = JSON.getGson().fromJson(appliedCustomerBillRates, AppliedCustomerBillingRate[].class);
			
			for(AppliedCustomerBillingRate bill: bills) {
				bill.setName("Applied Customer Bill Rate #" + (int)Math.round(Math.random() * 100));
				bill.setDescription("Example for Applied Customer Bill Rate!");
				
				AppliedCustomerBillingRateCreate createApply = AppliedCustomerBillingRateCreate.fromJson(bill.toJson());
				AppliedCustomerBillingRate created = appliedCustomerBillingRate.createAppliedCustomerBillingRate(createApply);
				logger.info("AppliedCustomerBillRate saved with id: {}", created.getId());
				ids.add(created.getId());
			}
			
		} catch (Exception e) {
			logger.info("AppliedCustomerBillingRate not saved!");
			logger.error(e.getMessage());
		}
		return ids;
	}

	
	private boolean isBilled1() {
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

					//TODO check if it is already billed
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
	
	private String bill(Product product) {
		logger.info("Start bill process for productId: " + product.getId());
		// Invocazione del billing in modalità consuntiva (input Product -> output Bill) 
		
		// Invocazione dell'invoicing in modalità consuntiva (input Bill -> output Bill) 
		
		//TODO get the product order in another way!!!
		String orderJson = getProductOrderJson();
		ResponseEntity<String> response = invoicing(orderJson);	
		logger.info("Status code {}", response.getStatusCode());
		//logger.info("Order with Tax \n {} ", response.getBody() );
		
		
		// save the bill (AppliedCustomerBillRate) in TMForum
		logger.info("Saving AppliedCustomerBillRate in TMForum");
		String acbrJson = getAppliedCustomerBillingrateJson();
		//System.out.println("Payload for AppliedCustomerBillRate:\n" + acbrJson);
		
		try {
			AppliedCustomerBillingRateCreate acbr = AppliedCustomerBillingRateCreate.fromJson(acbrJson);
			acbr.setName("Test Applied Customer Bill Rate #" + (int)Math.round(Math.random() * 100));
			acbr.setDescription("Applied Customer Bill Rate for prod: " + product.getId());
			AppliedCustomerBillingRate created = appliedCustomerBillingRate.createAppliedCustomerBillingRate(acbr);
			logger.info("AppliedCustomerBillRate saved with id: {}", created.getId());
			return created.getId();
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			logger.info("AppliedCustomerBillingRate not saved!");
			logger.error(e.getMessage());
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	/*
  	Verificare quando è il momento di generare il Bill per un Product (check a partire dalla lista dei Product)-> Tipi di Product che si prende in considerazione -> “recurring” (escludiamo il “pay per use”) 

   	Check se il Bill per il Product in questione è già stato calcolato e salvato in tmforum 

	Invocazione del billing in modalità consuntiva (input Product -> output Bill) 
	
	Invocazione dell’invoicing in modalità consuntiva (input Bill -> output Bill) 
	
	Persistenza del Bill su tmforum 
	 */	
	
	
	

	
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
