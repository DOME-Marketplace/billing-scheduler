package it.eng.dome.billing.scheduler.service;

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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import it.eng.dome.billing.scheduler.tmf.TmfApiFactory;
import it.eng.dome.brokerage.billing.dto.BillingRequestDTO;
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

	@Autowired
	private TmfApiFactory tmfApiFactory;

	private ProductApi productApi;
	private AppliedCustomerBillingRateApi appliedCustomerBillingRate;

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
	}

	public void calculateBuilling() throws Exception {

		// retrieve all products
		logger.info("Retrieve all products");
		// TODO - how to improve filtering of product
		List<Product> products = productApi.listProduct(null, null, null);
		logger.info("Number of Product found: {} ", products.size());

		OffsetDateTime now = OffsetDateTime.now(); /*OffsetDateTime.parse("2024-12-31T13:14:33.213Z");*/   
		int count = 0;

		for (Product product : products) {
			logger.debug("Product item # {} - {}", ++count, product.getName());
			logger.debug("Analyze productId: " + product.getId() + " with status: " + product.getStatus());

			// Check #1 - status=active
			if (product.getStatus() == ProductStatusType.ACTIVE) {

				List<ProductPrice> pprices = product.getProductPrice();
				logger.debug("Number of ProductPrices found: {} ", pprices.size());

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
							logger.debug("Use Case - Get RecurringPeriod from ProductOfferingPrice for product: " + product.getId());
							recurringPeriod = getRecurringPeriod(pprice.getProductOfferingPrice().getId());
							logger.info("Get recurring period {} from ProductOfferingPrice", recurringPeriod);

						} else if (pprice.getRecurringChargePeriod() != null) {// RecurringPeriod from RecurringChargePeriod
							logger.debug("Use Case - Get RecurringPeriod from RecurringChargePeriod for product: " + product.getId());
							recurringPeriod = pprice.getRecurringChargePeriod();
							logger.info("Get recurring period {} for RecurringChargePeriod", recurringPeriod);
						}
						logger.debug("Recurring period found: " + recurringPeriod);

						if (recurringPeriod != null && product.getStartDate() != null) {
							OffsetDateTime nextBillingTime = BillingUtils.getNextBillingTime(product.getStartDate(), now, recurringPeriod);
							OffsetDateTime previousBillingTime = BillingUtils.getPreviousBillingTime(nextBillingTime, recurringPeriod);

							if (nextBillingTime != null) {
								logger.debug("StartDate: " + product.getStartDate());
								logger.debug("NextDate: " + nextBillingTime);
								logger.debug("PreviuosDate: " + previousBillingTime);
								logger.debug("CurrentDate: " + now);

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
							logger.debug("No RecurringPeriod found or product.startDate valid");
						}

					} else {
						logger.debug("No bill for productId {} because priceType = {} is not recurring status", product.getId(), pprice.getPriceType());
					}
				}

				logger.info("Number of item for billing found: {}", productPrices.size());
				for (Map.Entry<String, List<ProductPrice>> entry : productPrices.entrySet()) {

					String key = entry.getKey();
					TimePeriod tp = timePeriods.get(key).get(0);

					if (!timePeriods.get(key).isEmpty()) {
						logger.debug("TimePeriodo - startDate: " + tp.getStartDateTime() + " - endDate: " + tp.getEndDateTime());
						List<ProductPrice> pps = entry.getValue();
						for (ProductPrice pp : pps) {
							logger.debug(pp.getName() + " || " + pp.getPriceType());
						}

						// Verify if the billing is already done
						if (!isAlreadyBilled(product, tp, pps)) {
							logger.debug("Apply billing - AppliedCustomerBillingRate: " + product.getId());

							// TODO invoke billing-engine
							//String applied = getAppliedCustomerBillingrateJson();
							ResponseEntity<String> applied = getAppliedCustomerBillingRates(product, tp, pps);
							if (applied != null) {
								//logger.info(applied.getStatusCode().toString());
								//logger.info(applied.getBody());
								
								// invoke invoicing-service
								ResponseEntity<String> invoicing = invoicing(applied.getBody());
								logger.info("Status code {}", invoicing.getStatusCode());
								// logger.info("AppliedCustomerBillingRate with Tax \n {} ", invoicing.getBody());
								String appliedCustomerBillingRatesJson = invoicing.getBody();
								logger.debug("Invoicing body \n{}", appliedCustomerBillingRatesJson);
								
								// Save AppliedCustomerBillingRate[] with taxes in TMForum
								List<String> ids = bill(appliedCustomerBillingRatesJson);
								logger.info("Saved #{} AppliedCustomerBillingRate", ids.size());
								logger.debug("AppliedCustomerBillingRate ids: {}", ids);
							}
							

							
						} else {
							logger.debug("Billing already done for productId: {}", product.getId());
						}
					}
				}
			} else {
				logger.debug("Bill skipped for productId {} because status ({}) is not active", product.getId(), product.getStatus());
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

	private boolean isAlreadyBilled(Product product, TimePeriod tp, List<ProductPrice> productPrices) {
		logger.info("Verifying product is billed ...");
		boolean isBilled = false;
		try {
			List<AppliedCustomerBillingRate> billed = appliedCustomerBillingRate.listAppliedCustomerBillingRate("product,periodCoverage", null, null);
			logger.debug("Number of AppliedCustomerBillingRate found: {} ", billed.size());

			logger.info("ProductId to verify: {}", product.getId());
			
			for (AppliedCustomerBillingRate bill : billed) {
				String id = bill.getProduct().getId();

				if (id.equals(product.getId())) {
					logger.debug("Step 1 - found AppliedCustomerBillingRate with the same ProductId");
					if (tp.equals(bill.getPeriodCoverage())) {
						logger.debug("Step 2 - found PeriodCoverage with the same TimePeriod");

						// TODO check productPrices
						// ?????
						if (!verifyExistProductPrices(product, productPrices)) {
							logger.info("Check verifyExistProductPrices = false ");
						}else {
							logger.info("Found ProductPrices");
						}

						logger.info("Found product already billed");
						return true;
					} else {
						logger.debug("Stopped verifying: different TimePeriod");
					}
				} else {
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

	/* TO DELETE */
	
	private ResponseEntity<String> getAppliedCustomerBillingRates(Product product, TimePeriod tp, List<ProductPrice> productPrices) {
		logger.info("Running bill task");
		BillingRequestDTO billRequestDTO = new BillingRequestDTO();
		billRequestDTO.setProduct(product);
		billRequestDTO.setTimePeriod(tp);
		billRequestDTO.setProductPrice((ArrayList<ProductPrice>) productPrices);
		
		//logger.info("TEST\n" + JSON.getGson().toJson(billRequestDTO));
		String payload = JSON.getGson().toJson(billRequestDTO);
		//TODO ProductStatusType => Solve this bugfix
		payload = payload.replaceAll("active", "ACTIVE");
		logger.info(payload);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(payload, headers);
		return restTemplate.postForEntity(billing.billinEngine + "/billing/bill", request, String.class);
	}
}
