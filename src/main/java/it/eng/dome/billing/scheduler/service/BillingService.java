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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
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

import it.eng.dome.billing.scheduler.tmf.TmfApiFactory;
import it.eng.dome.billing.scheduler.utils.Utils;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.ProductApis;
import it.eng.dome.brokerage.api.ProductOfferingPriceApis;
import it.eng.dome.brokerage.billing.utils.BillingUtils;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;
import it.eng.dome.tmforum.tmf678.v4.JSON;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingService implements InitializingBean {

	private final Logger logger = LoggerFactory.getLogger(BillingService.class);
	private final static String CONCAT_KEY = "|";
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
	private final static String SPACE = "- ";
	
	private final Integer PPU_START_DAY = 2;
	
	@Autowired
	private TmfApiFactory tmfApiFactory;
	
	@Autowired
	protected BillingFactory billing;

	@Autowired
	protected RestTemplate restTemplate;
	
	private ProductApis productApis;
	private AppliedCustomerBillRateApis appliedCustomerBillRateApis;
	private ProductOfferingPriceApis productOfferingPrices;

	@Override
	public void afterPropertiesSet() throws Exception {
		productApis = new ProductApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
		productOfferingPrices = new ProductOfferingPriceApis(tmfApiFactory.getTMF620CatalogApiClient());
		appliedCustomerBillRateApis = new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
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

		// add filter - consider only product with status = active 
		Map<String, String> filter = new HashMap<String, String>();
		filter.put("status", ProductStatusType.ACTIVE.getValue()); 
		
		// get only products with active status
		List<Product> products = productApis.getAllProducts(null, filter);

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
	
							if ((pprice.getPriceType() != null)) {
								
								logger.info("{}PriceType {} found for the productPrice", getIndentation(2), pprice.getPriceType());
													
								String priceType = Utils.BillingPriceType.normalize(pprice.getPriceType());
								
								// check priceTypes are complaint with service scheduler																
								if (priceType != null) {
									// we consider all priceType: recurring, recurring-prepaid, recurring-postpaid, pay-per-use 
									logger.info("{}PriceType recognize: {}", getIndentation(2), priceType);
									
									String recurringPeriod = null;
			
									// Retrieve the RecurringPeriod => there are 2 use cases (RecurringPeriod or RecurringChargePeriod)
									if (pprice.getProductOfferingPrice() != null) {
										// Use Case 1 - RecurringPeriod from ProductOfferingPrice
										
										// recurringChargePeriodType + recurringChargePeriodLength
										logger.debug("{}Get RecurringPeriod from ProductOfferingPrice for product: {}", getIndentation(2), product.getId());
										recurringPeriod = getRecurringPeriod(pprice.getProductOfferingPrice().getId());
										logger.info("{}Get recurring period {} from ProductOfferingPrice", getIndentation(2), recurringPeriod);
			
									} else if (pprice.getRecurringChargePeriod() != null) { 
										// Use Case 2 - RecurringPeriod from RecurringChargePeriod
										
										logger.debug("{}Get RecurringPeriod from RecurringChargePeriod for product: {}", getIndentation(2), product.getId());
										recurringPeriod = pprice.getRecurringChargePeriod();
										logger.info("{}Get recurring period {} for RecurringChargePeriod", getIndentation(2), recurringPeriod);
									}
									logger.debug("{}Recurring period found: {}", getIndentation(2), recurringPeriod);
			
									if (recurringPeriod != null && product.getStartDate() != null) {
										
										//TODO - decrease 2 days for pay-per-use => now - 2 days
										if ("pay_per_use".equalsIgnoreCase(priceType)) {
											now = now.minusDays(PPU_START_DAY);
											logger.debug("Pay per use is delayed by {} days compared to now", PPU_START_DAY);
											logger.info("Start pay-per-use task at: {}", now);
										}
										
										OffsetDateTime nextBillingTime = BillingUtils.getNextBillingTime(product.getStartDate(), now, recurringPeriod);
										OffsetDateTime previousBillingTime = BillingUtils.getPreviousBillingTime(nextBillingTime, recurringPeriod);
			
										if (nextBillingTime != null) {
											logger.debug("{}Billing dateTime for the product {}:", getIndentation(2), product.getId());
											logger.debug("{}- StartDate: {}", getIndentation(2), product.getStartDate());
											logger.debug("{}- NextDate: {}", getIndentation(2), nextBillingTime);
											logger.debug("{}- PreviuosDate: {}", getIndentation(2), previousBillingTime);
											logger.debug("{}- CurrentDate: {}", getIndentation(2), now);
			
											// Get numbers of days missing before to start the next bill
											long days = ChronoUnit.DAYS.between(now, nextBillingTime);
											logger.debug("{}Missing days before starting the next bill: {}", getIndentation(2), days);
																				
											long diffPreviousBillingAndNextBilling = ChronoUnit.DAYS.between(previousBillingTime, nextBillingTime);
											logger.debug("{}Difference from PreviuosDate and NextDate for bill (in days): {}", getIndentation(2), diffPreviousBillingAndNextBilling);
			
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
									logger.debug("{}No priceType recognized. Allowed priceType: {}", getIndentation(2), Utils.BillingPriceType.getAllowedValues());
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
							
							// Verify if the billing is already done
							//FIXME - dovrebbe essere sia billed che not billed => l'unico filtro potrebbe essere il priceType
							if (!isAlreadyBilled(product, tp, pps, priceType)) {
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
								logger.debug("{}Bill already billed for productId: {}", getIndentation(2), product.getId());
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
	 * @throws it.eng.dome.tmforum.tmf620.v4.ApiException
	 */
	private String getRecurringPeriod(String id) throws it.eng.dome.tmforum.tmf620.v4.ApiException {
		logger.info("{}Retrieve the RecurringPeriod for ProductOfferingPriceId: {}", getIndentation(3), id);

		ProductOfferingPrice pop = productOfferingPrices.getProductOfferingPrice(id, null);
		if (pop != null) {
			logger.debug("{}Found RecurringChargePeriodLength: {} - RecurringChargePeriodType: {}", getIndentation(3), pop.getRecurringChargePeriodLength(), pop.getRecurringChargePeriodType());
			return pop.getRecurringChargePeriodLength() + " " + pop.getRecurringChargePeriodType();
		} else {
			logger.warn("{}Cannot found the ProductOfferingPrice for productId: {}", getIndentation(3), id);
			return null;
		}
		
	}

	/**
	 * Verify if the bill is already billed by using product, timePeriod, and productPrice list 
	 * 
	 * @param product 
	 * @param tp
	 * @param productPrices
	 * @return boolean 
	 * @throws it.eng.dome.tmforum.tmf678.v4.ApiException
	 */
	private boolean isAlreadyBilled(Product product, TimePeriod tp, List<ProductPrice> productPrices, String priceType) throws it.eng.dome.tmforum.tmf678.v4.ApiException {
		
		logger.info("{}Verifying product is already billed", getIndentation(3));
		boolean isBilled = false;

		logger.info("{}Retrieve the list of AppliedCustomerBillingRate", getIndentation(3));
		
		// add filter for AppliedCustomerBillingRate 
		Map<String, String> filter = new HashMap<String, String>();
		//filter.put("isBilled", "true"); // isBilled = true
		filter.put("rateType", priceType);
		//FIXME - we can add some filters i.e. periodCoverage.endDateTime, etc...
		//TODO - must be checked
		filter.put("periodCoverage.startDateTime.gt", tp.getStartDateTime().minusSeconds(1).toString());
		filter.put("periodCoverage.endDateTime.lt", tp.getEndDateTime().plusSeconds(1).toString());
		
		
		List<AppliedCustomerBillingRate> billed = appliedCustomerBillRateApis.getAllAppliedCustomerBillingRates("product,periodCoverage", filter);
		logger.debug("{}Number of AppliedCustomerBillingRate found: {}", getIndentation(3), billed.size());

		logger.info("{}ProductId to verify: {}", getIndentation(3), product.getId());
		
		for (AppliedCustomerBillingRate bill : billed) {
			if (bill.getProduct() != null) { // check if product is not null
				
				String id = bill.getProduct().getId();
				
				if (id.equals(product.getId())) {
					logger.debug("{}Verify bill - step 1 - found AppliedCustomerBillingRate with the same ProductId: {}", getIndentation(4), id);
					
					if (tp.equals(bill.getPeriodCoverage())) {					
						logger.debug("{}Verify bill - step 2 - found PeriodCoverage with the same TimePeriod", getIndentation(4));
						
						// TODO check productPrices - cosa fare se non esiste il pp : saltare?
						if (!verifyExistProductPrices(product, productPrices)) {
							logger.warn("{}Check verifyExistProductPrices = false", getIndentation(4));
							//TODO cosa fare in questo caso???
						}else {
							//logger.info("{}Found ProductPrices", getIndentation(4));
						}

						//default -> just to go ahead (no IF above is considerated)
						logger.info("{}Found product already billed with id: {}", getIndentation(4), bill.getId());
						return true;
					} else {
						logger.debug("{}Stopped verify bill: different TimePeriod", getIndentation(4));
					}
				} else {
					logger.debug("{}Stopped verify bill: AppliedCustomerBillingRate with different ProductId {}", getIndentation(4), id);
				}
			} else {
				logger.debug("{}Product cannot be null for the bill: {}", getIndentation(4), bill.getId());
			}			
		}

		logger.info("{}Product needs to be billed", getIndentation(3));
		return isBilled;
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
				AppliedCustomerBillingRate created = appliedCustomerBillRateApis.createAppliedCustomerBillingRate(createApply);
				logger.info("{}AppliedCustomerBillRate saved with id: {}", getIndentation(2), created.getId());
				ids.add(created.getId());
			}

		} catch (Exception e) {
			logger.info("{}AppliedCustomerBillingRate not saved!", getIndentation(2));
			logger.error("{}Error: {}",getIndentation(2), e.getMessage());
		}
		return ids;
	}
	
	
	/**
	 * Verify if the productOfferingPrice of the product is included in the productPrices list
	 * 
	 * @param product
	 * @param productPrices
	 * @return boolean
	 */
	private boolean verifyExistProductPrices(Product product, List<ProductPrice> productPrices) {
		List<ProductPrice> pprices = product.getProductPrice();
		boolean result = false;
		for (ProductPrice pprice : pprices) {

			if (pprice.getProductOfferingPrice() != null) { 
				//use case with ProductOfferingPrice
				
				String id = pprice.getProductOfferingPrice().getId();
				logger.debug("{}ProductOfferingPrice id {} to verify", getIndentation(5), id);
				for (ProductPrice productPrice : productPrices) {
					logger.info("{}Checking with pprice.id {} ", getIndentation(5), productPrice.getProductOfferingPrice().getId());
					if (id.equals(productPrice.getProductOfferingPrice().getId())) {
						logger.info("{}Match found for id {}", getIndentation(5), id);
						return true;
					}
				}
			}else { 
				//use case: no ProductOfferingPrice => default jump
				
				//TODO not implemented yet
				logger.info("{}No ProductOfferingPrice found", getIndentation(5));
				logger.info("{}TODO - default jump (not implemented yet)", getIndentation(5));
				//TODO is it required to perform other check?
				logger.warn("{}Is it required to perform other check?", getIndentation(5));
				result = true;
			}
		}
		return result;
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