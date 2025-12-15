package it.eng.dome.billing.scheduler.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.billing.scheduler.client.BillingProxyApiClient;
import it.eng.dome.billing.scheduler.controller.BillingSchedulerController;
import it.eng.dome.billing.scheduler.exception.BillingSchedulerValidationException;
import it.eng.dome.billing.scheduler.validator.TMFEntityValidator;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.brokerage.billing.utils.ProductOfferingPriceUtils;
import it.eng.dome.brokerage.model.BillCycleSpecification;
import it.eng.dome.brokerage.model.Invoice;
import it.eng.dome.tmforum.tmf620.v4.ApiException;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.validation.constraints.NotNull;

@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingSchedulerService {

	private final Logger logger = LoggerFactory.getLogger(BillingSchedulerService.class);
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
	
	@Autowired
	private BillCycleService billCycleService;
	
	@Autowired
	private TMFEntityValidator tmfEntityValidator;
	
	@Autowired
	private BillingProxyApiClient billingProxyApiClient;
	
	@Autowired
	private TmfPersistenceService tmfPersistenceService;
	
	@Value("${persistence.monthsBack}")
	private int monthsBack;
	
	private final ProductInventoryApis productInventoryApis;
	private final ProductCatalogManagementApis productCatalogManagementApis;
	
	private List<TimePeriod> billingPeriods = new ArrayList<TimePeriod>();

	public BillingSchedulerService(ProductInventoryApis productInventoryApis, 
			ProductCatalogManagementApis productCatalogManagementApis) {
		
		this.productInventoryApis = productInventoryApis;
		this.productCatalogManagementApis = productCatalogManagementApis;
	}

	
	/**
	 * Methods called by the {@link BillingSchedulerController} to start the management of BillCycle. 
	 * If the use of {@link BillCycleSpecification} is enabled, the billCycle is computed from the information stored in the {@link BillCycleSpecification} (NOT SUPPORTED YET).\n
	 * If the use of {@link BillCycleSpecification} is not enabled the billCycle(s) is computed considering the billingPeriod end dates of the Product's ProductPrice(s).
	 * The BillingProxy will be invoked to calculate the bills for a Product that fall in a billingPeriod.
	 * To guarantee that no bills are missed during the Billing Scheduler (BS) processing, the calculation of the bills 
	 * will be performed for all the billingPeriods that overlap the interval between date of the BS processing and going back of 'persistence.monthsBack' 
	 * All the {@link Invoice} returned by the invocation of the BillingProxy are persisted in the DOME persistence layer, if not yet done.
	 *  
	 * @param limitDate The limit date to stop the calculation of the BillCycle 
	 * @param billCycleSpecificationEnabled true if the use of BillCycleSpecification has been enabled, false otherwise
	 * @throws it.eng.dome.tmforum.tmf637.v4.ApiException 
	 */
	public void manageBillCycle(OffsetDateTime limitDate, boolean billCycleSpecificationEnabled) throws it.eng.dome.tmforum.tmf637.v4.ApiException{

		logger.info("Starting management of BillCycle at {}", limitDate.format(formatter));
		
		if(billCycleSpecificationEnabled) {
			calculateBillCycleFromBillCycleSpecs(limitDate);
		}
		else {	
			// Get all ACTIVE Product (100 at time)
			FetchUtils.streamAll(
					productInventoryApis::listProducts,  // method reference
			        null,                     	// fields
			        Map.of("status","active"),	// filter
			        100                         // pageSize
				)
				.forEach(product -> { 
					List<Invoice> invoices=new ArrayList<Invoice>();
					
					try {
						
						logger.info("##### Calculation of billingPeriods from ProductPrice components of Product {} #####",product.getId());
						// Validate Product
						tmfEntityValidator.validateProduct(product);
						
						// Calculate the billingPeriods of a Product that falls within the limit date.
						List<TimePeriod> billingPeriods=calculateBillingPeriod(product,limitDate);
						
						// Gets all the billingPeriods that overlap the interval [ limitDate - monthsBack  ,  limitDate ]
						List<TimePeriod> filteredBillingPeriods=filterBillingPeriods(billingPeriods, limitDate, monthsBack);
						String filteredPeriodsString = filteredBillingPeriods.stream()
							    .map(bp -> "[" + bp.getStartDateTime() + " - " + bp.getEndDateTime() + "]")
							    .collect(Collectors.joining(", "));

						logger.debug("Filtered BillingPeriods back of {} months: {}", monthsBack, filteredPeriodsString);
						
						// Invocation of BillingProxy for bills calculation
						for(TimePeriod billingPeriod:filteredBillingPeriods) {
							List<Invoice> invoicesInBillingPeriod= billingProxyApiClient.billingBill(product.getId(), billingPeriod);
							logger.debug("Number of invoices generated for Product {} in billingPeriod [{}-{}]:{}",
									product.getId(), billingPeriod.getStartDateTime(),billingPeriod.getEndDateTime(),invoicesInBillingPeriod.size());
							invoices.addAll(invoicesInBillingPeriod);
						}
						
						logger.debug("Total numeber of invoices generated in the interval [{}-{}]: {}",limitDate.minusMonths(monthsBack),limitDate,invoices.size());
						
						//Invoke persistence service to store invoices
						List<Invoice> persistedInvoices= tmfPersistenceService.persistAllInvoices(invoices, product.getId());
						String persistedInvoiceIdsString = persistedInvoices.stream()
							    .map(invoice -> invoice.getCustomerBill().getId())
							    .collect(Collectors.joining(", "));
						logger.debug("Persisted Invoices: ", persistedInvoiceIdsString);
						
					}catch(Exception e) {
						logger.error(e.getMessage());
						logger.error("Product '{}' skipped",product.getId());
						return;
					
					}
				}
				);	
		}
	}
	
	/*
	 * Method to calculate the billingPeriods of a Product that falls within the limit date.
	 * The billingPeriods are calculated considering all the billingPeriod end dates of its ProductPrice(s)
	 */
	private List<TimePeriod> calculateBillingPeriod(@NotNull Product product, @NotNull OffsetDateTime limitDate) throws IllegalArgumentException, ApiException, BillingSchedulerValidationException {

		logger.info("Calculation of current billingPeriod for Product '{}'", product.getId());
		
		// Get ProductPrice from Product
		List<ProductPrice> productPrices=product.getProductPrice();
		logger.debug("...ProductPrice(s) found: {}", productPrices.size());
		
		//Variable to collect, for all the ProductPrice of the Product, all the end dates of the billingPeriod(s) 
		Set<OffsetDateTime> billingPeriodsEndDates=new HashSet<OffsetDateTime>();
		
		// Get billingPeriod end dates for each ProductPrice
		for(ProductPrice pp: productPrices) {
			
			// Validate ProductPrice
			tmfEntityValidator.validateProductPrice(pp, product.getId());
			
			ProductOfferingPrice pop= ProductOfferingPriceUtils.getProductOfferingPrice(pp.getProductOfferingPrice().getId(), productCatalogManagementApis);
			
			// Validate POP
			tmfEntityValidator.validateProductOfferingPrice(pop);
	
			List<OffsetDateTime> popBillingPeriodEndDates= billCycleService.calculateBillingPeriodEndDates(ProductOfferingPriceUtils.getRecurringChargePeriod(pop), product.getStartDate(), limitDate);

			billingPeriodsEndDates.addAll(popBillingPeriodEndDates);
		}
		
		if(!billingPeriodsEndDates.isEmpty())
			// Get all billingPeriods for the Product from the calculated billingPeriod end dates of the ProductPrice(s)
			billingPeriods= billCycleService.calculateBillingPeriods(new ArrayList<OffsetDateTime>(billingPeriodsEndDates), product.getStartDate());
		
		if(!billingPeriods.isEmpty()) {
			String periodsString = billingPeriods.stream()
				    .map(bp -> "[" + bp.getStartDateTime() + " - " + bp.getEndDateTime() + "]")
				    .collect(Collectors.joining(", "));

			logger.debug("BillingPeriods for Product '{}': {}", product.getId(), periodsString);

			
		}else {
			logger.debug("No billingPeriod(s) found for Product '{}'", product.getId());
		}
		
		return billingPeriods;
	}

	
	/*
	 * TODO: Method not implemented yet. BillCycleSpecification not supported yet.
	 * @param date  
	 */
	private void calculateBillCycleFromBillCycleSpecs(OffsetDateTime date) {
		logger.info("Start management of BillCycle at: {}", date);
		throw new UnsupportedOperationException("Method not supported yet!");
	}
	
	/*
	 * Method to retrieve the billingPeriods that overlap the interval [ limitDate - monthsBack  ,  limitDate ]
	 * @param billingPeriods A list of TimePeriod representing the billingPeriods
	 * @param limitDate An OffsedDateTime representing the upper limit of the interval
	 * @param monthBack integer representing the number of month to go back (i.e., the lower limit of the interval)
	 */
	private List<TimePeriod> filterBillingPeriods(
	        List<TimePeriod> billingPeriods,
	        OffsetDateTime limitDate,
	        int monthsBack) {

	    OffsetDateTime threshold = limitDate.minusMonths(monthsBack);

	    return billingPeriods.stream()
	            // The period is valid if it overlaps the interval [threshold, limitDate]
	            .filter(bp ->
	                bp.getEndDateTime().isAfter(threshold) &&
	                bp.getStartDateTime().isBefore(limitDate)
	            )
	            .collect(Collectors.toList());
	}
	
	
	
}