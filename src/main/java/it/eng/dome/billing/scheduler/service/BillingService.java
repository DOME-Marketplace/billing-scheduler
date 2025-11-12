package it.eng.dome.billing.scheduler.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.brokerage.billing.utils.BillingPriceType;
import it.eng.dome.brokerage.billing.utils.BillingUtils;
import it.eng.dome.tmforum.tmf620.v4.ApiException;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductOfferingPriceRef;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;
import it.eng.dome.tmforum.tmf678.v4.JSON;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingService {

	private final Logger logger = LoggerFactory.getLogger(BillingService.class);
	private final static String CONCAT_KEY = "|";
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
	private final static String SPACE = "- ";
	
    @Value("${billing_pay_per_use.delayed_days}")
    public int delayedDays;

	
	@Autowired
	protected BillingFactory billing;

	@Autowired
	protected RestTemplate restTemplate;
	
	private final ProductInventoryApis productInventoryApis;
	private final ProductCatalogManagementApis productCatalogManagementApis;
	private final AppliedCustomerBillRateApis appliedCustomerBillRateApis;
	
	public BillingService(ProductInventoryApis productInventoryApis, 
			ProductCatalogManagementApis productCatalogManagementApis,
			AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
		
		this.productInventoryApis = productInventoryApis;
		this.productCatalogManagementApis = productCatalogManagementApis;
		this.appliedCustomerBillRateApis = appliedCustomerBillRateApis;
	}


	/**
	 * Method called by Scheduler process
	 * 
	 * @param now
	 * @throws it.eng.dome.tmforum.tmf637.v4.ApiException
	 * @throws it.eng.dome.tmforum.tmf620.v4.ApiException
	 * @throws it.eng.dome.tmforum.tmf678.v4.ApiException
	 */
	public void calculateBill(OffsetDateTime now) throws it.eng.dome.tmforum.tmf637.v4.ApiException, it.eng.dome.tmforum.tmf620.v4.ApiException, it.eng.dome.tmforum.tmf678.v4.ApiException {

		logger.info("Starting calculateBill at {}", now.format(formatter));

		// add filter - consider only products with status = active 
		Map<String, String> filter = new HashMap<String, String>();
		filter.put("status", ProductStatusType.ACTIVE.getValue()); 
		
		// get only products with active status
		//List<Product> products = productApis.getAllProducts(null, filter);		
		List<Product> products = FetchUtils.streamAll(
				productInventoryApis::listProducts,  // method reference
		        null,                     	// fields
		        filter,						// filter
		        100                         // pageSize
			).toList(); 
		

		if (products != null && !products.isEmpty()) { // verify if products list is not empty
			
			logger.info("Number of products found: {}", products.size());
			int count = 0;
			int appliedNumber = 0;
	
			for (Product product : products) {
				logger.debug("Product # {} - productId: {}", ++count, product.getId());
	
				// ProductPrice <> null 
				if (product.getProductPrice() != null) {
				
					List<ProductPrice> pprices = product.getProductPrice();
					logger.debug("{}Number of ProductPrices found: {} ", getIndentation(1), pprices.size());
	
					Map<String, List<TimePeriod>> timePeriods = new HashMap<>();
					Map<String, List<ProductPrice>> productPrices = new HashMap<>();
	
					if (pprices != null && !pprices.isEmpty()) {
						// product-price => only one
						for (ProductPrice pprice : pprices) {
							
							String priceType = retrievePriceType(pprice);
							
							if ((priceType != null)) {
								
								logger.info("{}PriceType {} found for the productPrice", getIndentation(2), priceType);

								String priceTypeNormalized = BillingPriceType.normalize(priceType);
								
								// check priceTypes are complaint with service scheduler																
								if (priceTypeNormalized != null) {
									// we consider all priceType: recurring, recurring-prepaid, recurring-postpaid, pay-per-use/usage 
									logger.info("{}PriceType recognize: {}", getIndentation(2), priceTypeNormalized);
									
									// retrieve the RecurringPeriod
									String recurringPeriod = retrieveRecurringPeriod(pprice);
									logger.debug("{}Recurring period found: {} - for product: {}", getIndentation(2), recurringPeriod, product.getId());
			
									if (recurringPeriod != null && product.getStartDate() != null) {
										
										// calculate the next billing time starting from StartDate time										
										OffsetDateTime nextBillingTime = BillingUtils.getNextBillingTime(product.getStartDate(), now, recurringPeriod);
										OffsetDateTime previousBillingTime = BillingUtils.getPreviousBillingTime(nextBillingTime, recurringPeriod);
			
										if (nextBillingTime != null) {
											logger.debug("{}Billing time of the product {} for type: {}", getIndentation(2), product.getId(), priceType);
											logger.debug("{}- StartDate: {}", getIndentation(2), product.getStartDate());
											logger.debug("{}- NextDate: {}", getIndentation(2), nextBillingTime);
											logger.debug("{}- PreviuosDate: {}", getIndentation(2), previousBillingTime);
											logger.debug("{}- CurrentDate: {}", getIndentation(2), now);
											
											
											// Note: pay per use must be paid after 2 days
											OffsetDateTime startTime = now;
											OffsetDateTime nextTime = nextBillingTime;
											if ("pay-per-use".equalsIgnoreCase(priceType) || "usage".equalsIgnoreCase(priceType)) {
												logger.debug("{}The pay-per-use payment is delayed by {} days compared to now: {}",getIndentation(3), delayedDays, now);
												//TODO check - decrease 2 days for pay-per-use for the scheduler task
												startTime = now.minusDays(delayedDays);	
												nextTime = nextBillingTime.minusDays(delayedDays);
												logger.info("{}The new time for the scheduled task for pay-per-use is: {}", getIndentation(3), startTime);
											}

											// Calculate the days difference between the previous and next billing 
											long diffPreviousBillingAndNextBilling = ChronoUnit.DAYS.between(previousBillingTime, nextBillingTime);
											logger.debug("{}Difference from PreviuosDate and NextDate (in days): {} - for recurrung period: {}", getIndentation(2), diffPreviousBillingAndNextBilling, recurringPeriod);
		
											// Get numbers of days missing before to start the next bill
											long days = ChronoUnit.DAYS.between(startTime, nextTime);
											logger.debug("{}Missing days before starting the next bill: {}", getIndentation(2), days);

			
											// days = 0 => time expired => start the bill process
											if (days == 0) {
												String keyPeriod = priceType + CONCAT_KEY + diffPreviousBillingAndNextBilling;
												logger.info("{}Bill required for productId: {}", getIndentation(1), product.getId());
												// Set TimePeriod
												TimePeriod tp = new TimePeriod();
												tp.setStartDateTime(previousBillingTime);
												tp.setEndDateTime(nextBillingTime);
			
												// grouped items with the same startDate and endDate (i.e. keyPeriod)
												timePeriods.put(keyPeriod, new ArrayList<>(Arrays.asList(tp)));
												productPrices.computeIfAbsent(keyPeriod, k -> new ArrayList<>()).add(pprice);
											}
										}
									} else {
										logger.debug("{}No RecurringPeriod found or product.startDate not valid", getIndentation(2));
									}
								} else {
									logger.debug("{}No priceType recognized. Allowed priceType: {}", getIndentation(2), BillingPriceType.getAllowedValues());
								}
		
							} else {
								logger.debug("{}No bill for productId {} because priceType is null", getIndentation(2), product.getId());
							}
						}
					} else {
						logger.warn("No ProductPrice found for the product {}", product.getId());
					}
					
				
					/* verify timePeriods/productPrices if it needs to bill */  

					logger.info("{}Number of ProductPrices for billing found for productId {}: {}", getIndentation(1), product.getId(), productPrices.size());
					for (Map.Entry<String, List<ProductPrice>> entry : productPrices.entrySet()) {
	
						String key = entry.getKey();
						TimePeriod tp = timePeriods.get(key).get(0);
	
						if (!timePeriods.get(key).isEmpty()) {
							logger.debug("{}TimePeriod - startDateTime: {} - endDateTime: {} ", getIndentation(2), tp.getStartDateTime(), tp.getEndDateTime());
							List<ProductPrice> pps = entry.getValue();
							
							String priceType = key.substring(0, key.indexOf(CONCAT_KEY));
							logger.debug("Applied selected by priceType: {}", priceType);

							if (!isAlreadyBilled(product, tp, priceType)) {
								logger.debug("{}Apply billing process for productId: {}", getIndentation(2), product.getId());
	
								if (product.getBillingAccount() != null) {
	
									logger.debug("{}Get AppliedCustomerBillingRates based by product, timePeriod, and productPrice", getIndentation(2));
									ResponseEntity<String> applied = getAppliedCustomerBillingRates(product, tp, pps);
									
									if (applied != null) {
										List<String> ids = saveBill(applied.getBody());
										logger.info("{}Number of AppliedCustomerBillingRate saved: {}", getIndentation(2), ids.size());
										logger.debug("{}AppliedCustomerBillingRate ids saved: {}", getIndentation(2), ids);
										appliedNumber += ids.size();
									}else {
										logger.warn("{}Cannot retrieve AppliedCustomerBillingRates", getIndentation(2));
									}
								} else {
									logger.warn("{}No Billing Account defined in the product {}", getIndentation(2), product.getId());
								}
							} else {
								logger.debug("{}Bill already created for productId: {}", getIndentation(2), product.getId());
							}
						}
					}
				} else { // ProductPrice = null
					logger.warn("{}Bill skipped for productId {} because ProductPrice is null",  getIndentation(1), product.getId());
				}
			}
			
			logger.info("Number of AppliedCustomerBillingRate created: {}", appliedNumber);
		} else {
			logger.warn("The list of Products is empty");
		}

	}
	
	/**
	 * Retrieve the RecurringPeriod => there are 2 use cases (in cascade mode):
	 *   1. via RecurringPeriod 
	 *   2. via RecurringChargePeriod
	 * 
	 * @param pprice
	 * @return recurringPeriod
	 */
	private String retrieveRecurringPeriod(ProductPrice pprice) {
		
		String recurringPeriod = null;
				
		// Use Case 1 - RecurringPeriod from ProductOfferingPrice
		if (pprice.getProductOfferingPrice() != null) { 			
			// recurringPeriod format: recurringChargePeriodLength + recurringChargePeriodType
			recurringPeriod = getRecurringPeriod(pprice.getProductOfferingPrice().getId());
			logger.info("{}Get recurring period {} from ProductOfferingPrice", getIndentation(2), recurringPeriod);
		} 
		
		// Use Case 2 - RecurringPeriod from RecurringChargePeriod
		if (recurringPeriod == null && pprice.getRecurringChargePeriod() != null) {
			recurringPeriod = pprice.getRecurringChargePeriod();
			logger.info("{}Get recurring period {} from RecurringChargePeriod", getIndentation(2), recurringPeriod);
		}
		
		return recurringPeriod;
	}
	
	/**
	 *  Retrieve the PriceType => there are 2 use cases (in cascade mode):
	 *   1. via ProductOfferingPrice 
	 *   2. via ProductPrice (directly)
	 * 
	 * @param pprice
	 * @return priceType
	 */
	private String retrievePriceType(ProductPrice pprice) {
		String priceType = null;
		
		// Use Case 1 - PriceType from ProductOfferingPrice
		if (pprice.getProductOfferingPrice() != null) { 			
			ProductOfferingPriceRef popRef = pprice.getProductOfferingPrice();
			//ProductOfferingPrice pop = productOfferingPrices.getProductOfferingPrice(popRef.getId(), null);
			try {
				ProductOfferingPrice pop = productCatalogManagementApis.getProductOfferingPrice(popRef.getId(), null);
				priceType = pop.getPriceType();
				logger.debug("{}Get priceType {} from ProductOfferingPrice", getIndentation(2), priceType);
				
			} catch (ApiException e) {
				logger.error("Error: {}", e.getMessage());
			}
		} 
		
		// Use Case 2 - PriceType from ProductPrice
		if (priceType == null && pprice.getPriceType() != null) {
			priceType = pprice.getPriceType();
			logger.debug("{}Get priceType {} directly from ProductPrice", getIndentation(2), priceType);
		}

		return priceType;
	 }
	
	/**
	 * 
	 * @param n - integer
	 * @return String - number of INTEND (space) for indentation in the log
	 */
	private String getIndentation(int n) {
		if (n <= 1) {
			return SPACE;
		} else {
			return SPACE + getIndentation(n - 1);
		}
	}

	/**
	 * 
	 * @param id - productOfferingPriceId
	 * @return String - RecurringPeriod as RecurringChargePeriodLength + RecurringChargePeriodType
	 */
	private String getRecurringPeriod(String id) {
		logger.info("{}Retrieve the RecurringPeriod for ProductOfferingPriceId: {}", getIndentation(3), id);

		//ProductOfferingPrice pop = productOfferingPrices.getProductOfferingPrice(id, null);
		try {
			ProductOfferingPrice pop = productCatalogManagementApis.getProductOfferingPrice(id, null);
			if (pop != null) {
				logger.debug("{}Found RecurringChargePeriodLength: {} - RecurringChargePeriodType: {}", getIndentation(3), pop.getRecurringChargePeriodLength(), pop.getRecurringChargePeriodType());
				return pop.getRecurringChargePeriodLength() + " " + pop.getRecurringChargePeriodType();
			} else {
				logger.warn("{}Cannot found the ProductOfferingPrice for productId: {}", getIndentation(3), id);
				return null;
			}
		} catch (ApiException e) {
			logger.error("Error: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Verify if the bill is already created for billing by using product, timePeriod, and priceType 
	 * 
	 * @param product 
	 * @param tp
	 * @param priceType
	 * @return boolean 
	 * @throws it.eng.dome.tmforum.tmf678.v4.ApiException
	 */
	private boolean isAlreadyBilled(Product product, TimePeriod tp, String priceType) throws it.eng.dome.tmforum.tmf678.v4.ApiException {
		
		logger.info("{}Verifying product {} is already created for billing", getIndentation(3), product.getId());
		
		// set filters to retrieve the AppliedCustomerBillingRate 
		Map<String, String> filter = new HashMap<String, String>();
		filter.put("rateType", priceType);
		filter.put("periodCoverage.endDateTime.eq", tp.getEndDateTime().toString());
		filter.put("periodCoverage.startDateTime.eq", tp.getStartDateTime().toString());
		filter.put("product.id", product.getId());
		
		//List<AppliedCustomerBillingRate> billed = appliedCustomerBillRateApis.getAllAppliedCustomerBillingRates("isBilled", filter);
		List<AppliedCustomerBillingRate> billed = FetchUtils.streamAll(
				appliedCustomerBillRateApis::listAppliedCustomerBillingRates,    // method reference
		        "isBilled",                       		   // fields
		        filter,    								  // filter
		        100                                       // pageSize
		).toList(); 
		
		logger.debug("{}Number of AppliedCustomerBillingRate found: {}", getIndentation(3), billed.size());
		
		if (billed.isEmpty()) {
			// no AppliedCustomerBillingRate found
			logger.info("{}Product needs to be billed", getIndentation(3));
		}
			
		return !billed.isEmpty();
	}

	/**
	 * Save AppliedCustomerBillRate in the TMForum
	 * 
	 * @param appliedCustomerBillRates
	 * @return List of ids
	 */
	private List<String> saveBill(String appliedCustomerBillRates) {
		logger.info("{}Saving the bill in TMForum ...", getIndentation(2));

		List<String> ids = new ArrayList<String>();
		logger.debug("AppliedCustomerBillRates: {}", appliedCustomerBillRates);

		try {
			AppliedCustomerBillingRate[] bills = JSON.getGson().fromJson(appliedCustomerBillRates, AppliedCustomerBillingRate[].class);

			//TODO verify if this array has got just one AppliedCustomerBillingRate
			for (AppliedCustomerBillingRate bill : bills) {
				// bill.setName("Applied Customer Bill Rate - " + bill.getId());
				// bill.setDescription("Billing Scheduler generated the bill for " + bill.getProduct().getId());

				AppliedCustomerBillingRateCreate createApply = AppliedCustomerBillingRateCreate.fromJson(bill.toJson());
				String id = appliedCustomerBillRateApis.createAppliedCustomerBillingRate(createApply);
				logger.info("{}AppliedCustomerBillRate saved with id: {}", getIndentation(2), id);
				ids.add(id);
			}

		} catch (Exception e) {
			logger.info("{}AppliedCustomerBillingRate not saved!", getIndentation(2));
			logger.error("{}Error: {}",getIndentation(2), e.getMessage());
		}
		return ids;
	}
	
	
	/**
	 * 
	 * @param product
	 * @param tp
	 * @param productPrices
	 * @return List of AppliedCustomerBillingRates
	 */
	private ResponseEntity<String> getAppliedCustomerBillingRates(Product product, TimePeriod tp, List<ProductPrice> productPrices) {
		logger.info("{}Calling bill proxy endpoint: {}", getIndentation(2), billing.billinProxy + "/billing/bill");
		String payload = getBillRequestDTOtoJson(product, tp, productPrices);

		logger.debug("{}Payload to get appliedCustomerBillingRate: {}", getIndentation(3), payload);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(payload, headers);
		return restTemplate.postForEntity(billing.billinProxy + "/billing/bill", request, String.class);
	}
	
	
	/**
	 * Create BillRequestDTO in JSON format (String)
	 * 
	 * @param product
	 * @param tp
	 * @param productPrices
	 * @return String
	 */
	private String getBillRequestDTOtoJson(Product product, TimePeriod tp, List<ProductPrice> productPrices) {
		// product
		String productJson = product.toJson();
		
		// timePeriod
		String timePeriodJson = tp.toJson();
		
		// productPriceListJson
		StringBuilder productPriceListJson = new StringBuilder("[");
		for (int i = 0; i < productPrices.size(); i++) {
            if (i > 0) {
            	productPriceListJson.append(", ");
            }
            productPriceListJson.append(productPrices.get(i).toJson());
        }
		productPriceListJson.append("]");

		String billingJson = "{ \"product\": " + capitalizeStatus(productJson) + ", \"timePeriod\": "+ timePeriodJson + ", \"productPrice\": "+ productPriceListJson.toString() +"}";
		//logger.debug("Billing payload:\n" + billingJson);
		return billingJson;
	} 
	
	private String capitalizeStatus(String json) {
		ObjectMapper objectMapper = new ObjectMapper();
		String capitalize = json;
		try {
			ObjectNode jsonNode = (ObjectNode) objectMapper.readTree(json);
			String status = jsonNode.get("status").asText();
			jsonNode.put("status", status.toUpperCase());
			return objectMapper.writeValueAsString(jsonNode);

		} catch (Exception e) {
			return capitalize;
		}
	}
}