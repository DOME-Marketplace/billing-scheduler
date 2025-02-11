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
import it.eng.dome.brokerage.billing.utils.BillingUtils;
import it.eng.dome.tmforum.tmf620.v4.api.ProductOfferingPriceApi;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.api.ProductApi;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;
import it.eng.dome.tmforum.tmf678.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.JSON;
import it.eng.dome.tmforum.tmf678.v4.api.AppliedCustomerBillingRateApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingService implements InitializingBean {

	private final Logger logger = LoggerFactory.getLogger(BillingService.class);
	private final static String PREFIX_KEY = "period-";
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
	private final static String INTEND = "  ";
	
	@Autowired
	private TmfApiFactory tmfApiFactory;
	
	@Autowired
	protected BillingFactory billing;

	@Autowired
	protected RestTemplate restTemplate;
	
	private ProductApi productApi;
	private AppliedCustomerBillingRateApi appliedCustomerBillingRate;
	private ProductOfferingPriceApi productOfferingPrice;

	@Override
	public void afterPropertiesSet() throws Exception {
		productApi = new ProductApi(tmfApiFactory.getTMF637ProductInventoryApiClient());
		productOfferingPrice = new ProductOfferingPriceApi(tmfApiFactory.getTMF620CatalogApiClient());
		appliedCustomerBillingRate = new AppliedCustomerBillingRateApi(tmfApiFactory.getTMF678ProductInventoryApiClient());
	}

	public void calculateBuilling(OffsetDateTime now) {

		logger.info("Starting calculateBuilling at {}", now.format(formatter));

		logger.debug("BasePath of productApi: {}", productApi.getApiClient().getBasePath());

		try { 
			// retrieve all products
			logger.info("Retrieve all products from productApi.listProduct() method");
			// TODO - how to improve filtering of product
			List<Product> products = productApi.listProduct(null, null, null);

			logger.info("Number of Products found: {} ", products.size());

			int count = 0;

			for (Product product : products) {
				logger.debug("Product item # {} - {}", ++count, product.getName());
				logger.debug("{}Analyze productId: {} with status: {}", getIntentation(1), product.getId(), product.getStatus());
				
				// Check #1 - status=active
				if (product.getStatus() == ProductStatusType.ACTIVE) {

					List<ProductPrice> pprices = product.getProductPrice();
					logger.debug("{}Number of ProductPrices found: {} ", getIntentation(1),  pprices.size());

					Map<String, List<TimePeriod>> timePeriods = new HashMap<>();
					Map<String, List<ProductPrice>> productPrices = new HashMap<>();

					for (ProductPrice pprice : pprices) {

						// Check #2 - priceType = recurring
						// TODO verify if it must use recurring-prepaid and recurring-postpaid
						if ("recurring".equals(pprice.getPriceType().toLowerCase())) {

							String recurringPeriod = null;

							// Retrieve the RecurringPeriod
							if (pprice.getProductOfferingPrice() != null) {// RecurringPeriod from ProductOfferingPrice
								// GET recurringChargePeriodType + recurringChargePeriodLength
								logger.debug("{}Use Case - Get RecurringPeriod from ProductOfferingPrice for product: {}", getIntentation(2), product.getId());
								recurringPeriod = getRecurringPeriod(pprice.getProductOfferingPrice().getId());
								logger.info("{}Get recurring period {} from ProductOfferingPrice", getIntentation(2), recurringPeriod);

							} else if (pprice.getRecurringChargePeriod() != null) {// RecurringPeriod from RecurringChargePeriod
								logger.debug("{}Use Case - Get RecurringPeriod from RecurringChargePeriod for product: {}", getIntentation(2), product.getId());
								recurringPeriod = pprice.getRecurringChargePeriod();
								logger.info("{}Get recurring period {} for RecurringChargePeriod", getIntentation(2), recurringPeriod);
							}
							logger.debug("{}Recurring period found: {}", getIntentation(2), recurringPeriod);

							if (recurringPeriod != null && product.getStartDate() != null) {
								OffsetDateTime nextBillingTime = BillingUtils.getNextBillingTime(product.getStartDate(), now, recurringPeriod);
								OffsetDateTime previousBillingTime = BillingUtils.getPreviousBillingTime(nextBillingTime, recurringPeriod);

								if (nextBillingTime != null) {
									logger.debug("{}StartDate: {} - NextDate: {}", getIntentation(2), product.getStartDate(), nextBillingTime);
									logger.debug("{}PreviuosDate: {} - PreviuosDate: {}", getIntentation(2), previousBillingTime, now);

									// Get numbers of days missing before to start the next billing
									long days = ChronoUnit.DAYS.between(now, nextBillingTime);
									String keyPeriod = PREFIX_KEY + ChronoUnit.DAYS.between(previousBillingTime, nextBillingTime);

									// days = 0 => time expired => start the bill process
									if (days == 0) {
										// Get TimePeriod and ProductPrice for billing
										TimePeriod tp = new TimePeriod();
										tp.setStartDateTime(previousBillingTime);
										tp.setEndDateTime(nextBillingTime);

										// grouped items with the same startDate and endDate (i.e. keyPeriod)
										timePeriods.put(keyPeriod, new ArrayList<>(Arrays.asList(tp)));
										productPrices.computeIfAbsent(keyPeriod, k -> new ArrayList<>()).add(pprice);
									}
								}
							} else {
								logger.debug("{}No RecurringPeriod found or product.startDate valid", getIntentation(2));
							}

						} else {
							logger.debug("{}No bill for productId {} because priceType = {} is not recurring status", getIntentation(2), product.getId(), pprice.getPriceType());
						}
					}

					logger.info("{}Number of item for billing found: {}",  getIntentation(1), productPrices.size());
					for (Map.Entry<String, List<ProductPrice>> entry : productPrices.entrySet()) {

						String key = entry.getKey();
						TimePeriod tp = timePeriods.get(key).get(0);

						if (!timePeriods.get(key).isEmpty()) {
							logger.debug("{}TimePeriod - startDateTime: {} - endDateTime: {} ", getIntentation(2), tp.getStartDateTime(), tp.getEndDateTime());
							List<ProductPrice> pps = entry.getValue();
							/*for (ProductPrice pp : pps) {
								logger.debug(pp.getName() + " || " + pp.getPriceType());
							}*/
							logger.debug(product.toJson());

							// Verify if the billing is already done
							if (!isAlreadyBilled(product, tp, pps)) {
								logger.debug("{}Apply billing - AppliedCustomerBillingRate for productId: {}", getIntentation(2), product.getId());

								if (product.getBillingAccount() != null) {

									// TODO invoke billing-proxy
									// String applied = getAppliedCustomerBillingrateJson();
									ResponseEntity<String> applied = getAppliedCustomerBillingRates(product, tp, pps);
									if (applied != null) {
										// logger.info(applied.getStatusCode().toString());
										// logger.info(applied.getBody());

										// TODO remove invoicing-service => it's included in billing-proxy
										// invoke invoicing-service
										/*
										 * ResponseEntity<String> invoicing = invoicing(applied.getBody());
										 * logger.info("Status code {}", invoicing.getStatusCode()); //
										 * logger.info("AppliedCustomerBillingRate with Tax \n {} ",
										 * invoicing.getBody()); String appliedCustomerBillingRatesJson =
										 * invoicing.getBody(); logger.debug("Invoicing body \n{}",
										 * appliedCustomerBillingRatesJson);
										 */

										// Save AppliedCustomerBillingRate[] with taxes in TMForum
										// List<String> ids = bill(appliedCustomerBillingRatesJson);
										List<String> ids = bill(applied.getBody());
										logger.info("{}Saved #{} AppliedCustomerBillingRate", getIntentation(2), ids.size());
										logger.debug("{}AppliedCustomerBillingRate ids: {}", getIntentation(2), ids);
									}
								} else {
									logger.warn("{}No Billing Account defined in the product {}", getIntentation(2), product.getId());
								}
							} else {
								logger.debug("{}Billing already done for productId: {}", getIntentation(2), product.getId());
							}
						}
					}
				} else {
					logger.debug("{}Bill skipped for productId {} because status ({}) is not active",  getIntentation(1), product.getId(), product.getStatus());
				}
			}
		} catch (it.eng.dome.tmforum.tmf637.v4.ApiException e) {
			logger.error("Error: {}", e.getMessage());
		}
	}
	
	private String getIntentation(int n) {
		if (n <= 1) {
			return INTEND;
		} else {
			return INTEND + getIntentation(n -1);
		}
	}

	private String getRecurringPeriod(String id) {
		try {
			ProductOfferingPrice pop = productOfferingPrice.retrieveProductOfferingPrice(id, null);
			logger.debug("{}RecurringChargePeriodLength found: {}", getIntentation(2), pop.getRecurringChargePeriodLength());
			logger.debug("{}getRecurringChargePeriodType found: {}", getIntentation(2), pop.getRecurringChargePeriodType());
			return pop.getRecurringChargePeriodLength() + " " + pop.getRecurringChargePeriodType();
		} catch (it.eng.dome.tmforum.tmf620.v4.ApiException e) {
			logger.error("{}Error: {}", getIntentation(2), e.getMessage());
			return null;
		}
	}

	private boolean isAlreadyBilled(Product product, TimePeriod tp, List<ProductPrice> productPrices) {
		logger.info("{}Verifying product is already billed ...", getIntentation(3));
		boolean isBilled = false;
		try {
			logger.info("{}Retrieve the list of AppliedCustomerBillingRate", getIntentation(3));
			List<AppliedCustomerBillingRate> billed = appliedCustomerBillingRate.listAppliedCustomerBillingRate("product,periodCoverage", null, null);
			logger.debug("{}Number of AppliedCustomerBillingRate found: {}", getIntentation(3), billed.size());

			logger.info("{}ProductId to verify: {}", getIntentation(3), product.getId());
			
			for (AppliedCustomerBillingRate bill : billed) {
				String id = bill.getProduct().getId();

				if (id.equals(product.getId())) {
					logger.debug("{}Step 1 - found AppliedCustomerBillingRate with the same ProductId", getIntentation(3));
					if (tp.equals(bill.getPeriodCoverage())) {
						logger.debug("{}Step 2 - found PeriodCoverage with the same TimePeriod", getIntentation(3));

						// TODO check productPrices
						// ?????
						if (!verifyExistProductPrices(product, productPrices)) {
							logger.info("{}Check verifyExistProductPrices = false", getIntentation(3));
						}else {
							logger.info("{}Found ProductPrices", getIntentation(3));
						}

						logger.info("{}Found product already billed", getIntentation(3));
						return true;
					} else {
						logger.debug("{}Stopped verifying: different TimePeriod", getIntentation(3));
					}
				} else {
					logger.debug("{}Stopped verifying: different ProductId", getIntentation(3));
				}
			}
		} catch (ApiException e) {
			logger.error(e.getMessage());
			return isBilled;
		}
		logger.info("{}Product needs to be billed", getIntentation(3));
		return isBilled;
	}

	private List<String> bill(String appliedCustomerBillRates) {
		logger.info("Starting the billing process");

		List<String> ids = new ArrayList<String>();

		try {
			AppliedCustomerBillingRate[] bills = JSON.getGson().fromJson(appliedCustomerBillRates, AppliedCustomerBillingRate[].class);

			for (AppliedCustomerBillingRate bill : bills) {
				bill.setName("Applied Customer Bill Rate #" + (int) Math.round(Math.random() * 100));
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
	
	private boolean verifyExistProductPrices(Product product, List<ProductPrice> productPrices) {
		List<ProductPrice> pprices = product.getProductPrice();
		boolean result = false;
		for (ProductPrice pprice : pprices) {

			if (pprice.getProductOfferingPrice() != null) { //case with ProductOfferingPrice
				String id = pprice.getProductOfferingPrice().getId();
				logger.debug("ProductOfferingPrice id {} ", id);
				for (ProductPrice productPrice : productPrices) {
					logger.info("Checking with pprice.id {} ", productPrice.getProductOfferingPrice().getId());
					if (id.equals(productPrice.getProductOfferingPrice().getId())) {
						logger.info("Match found for id {} ", id);
						return true;
					}
				}
			}else { //case no ProductOfferingPrice => default jump
				//TODO not implemented yet
				logger.info("TODO - default jump (not implemented yet)");
				result = true;
			}
		}
		return result;
	}

	
	private ResponseEntity<String> getAppliedCustomerBillingRates(Product product, TimePeriod tp, List<ProductPrice> productPrices) {
		logger.info("{}Calling bill task: {}", getIntentation(2), billing.billinProxy + "/billing/bill");
		String payload = getBillRequestDTOtoJson(product, tp, productPrices);
		//TODO ProductStatusType => Solve this bugfix
		//payload = payload.replaceAll("active", "ACTIVE");
		logger.debug("{}Payload appliedCustomerBillingRate:", getIntentation(3));
		logger.debug("{}{}", getIntentation(3), payload);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(payload, headers);
		return restTemplate.postForEntity(billing.billinProxy + "/billing/bill", request, String.class);
	}
	
	
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
