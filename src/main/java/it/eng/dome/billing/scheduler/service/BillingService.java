package it.eng.dome.billing.scheduler.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.billing.scheduler.controller.BillingSchedulerController;
import it.eng.dome.billing.scheduler.exception.BillingSchedulerValidationException;
import it.eng.dome.billing.scheduler.model.BillCycleSpecification;
import it.eng.dome.billing.scheduler.utils.ProductOfferingPriceUtils;
import it.eng.dome.billing.scheduler.validator.TMFEntityValidator;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.tmforum.tmf620.v4.ApiException;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.validation.constraints.NotNull;

@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingService {

	private final Logger logger = LoggerFactory.getLogger(BillingService.class);
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
	
	@Autowired
	private BillCycleService billCycleService;
	
	@Autowired
	private TMFEntityValidator tmfEntityValidator;
	
	private final ProductInventoryApis productInventoryApis;
	private final ProductCatalogManagementApis productCatalogManagementApis;
	
	private List<TimePeriod> billingPeriods = new ArrayList<TimePeriod>();

	public BillingService(ProductInventoryApis productInventoryApis, 
			ProductCatalogManagementApis productCatalogManagementApis) {
		
		this.productInventoryApis = productInventoryApis;
		this.productCatalogManagementApis = productCatalogManagementApis;
	}

	
	/**
	 * Methods called by the {@link BillingSchedulerController} to start the calculation, for each ACTIVE Product stored in TMForum, of the latest billingPeriod that falls within the limit date.\n
	 * If the use of {@link BillCycleSpecification} has been enabled, the billingPeriod is computed from the information stored in the BillCycleSpecifican (NOT SUPPORTED YET).\n
	 * If the use of BillCycleSpecification is not enabled the billingPeriod is computed considering the billingPeriod end dates of the Product's ProductPrice(s).
	 *  
	 * @param limitDate The limit date to stop the calculation of the billingPeriod 
	 * @param billCycleSpecificationEnabled true if the use of BillCycleSpecification has been enabled, false otherwise
	 */
	public void calculateBillCycle(OffsetDateTime limitDate, boolean billCycleSpecificationEnabled){

		logger.info("Starting calculateBillCycle at {}", limitDate.format(formatter));
		
		if(billCycleSpecificationEnabled) {
			calculateBillCycleFromBillCycleSpecs(limitDate);
		}
		else {
			logger.debug("Calculation of billCycle from ProductPrice components..."); 
			
			// Get all ACTIVE Product (100 at time)
			FetchUtils.streamAll(
					productInventoryApis::listProducts,  // method reference
			        null,                     	// fields
			        Map.of("status","active"),	// filter
			        100                         // pageSize
				) 
				.forEach(product -> { 
					try {
						// Validate Product
						tmfEntityValidator.validateProduct(product);
						
						// Calculate the latest billingPeriod of a Product that falls within the limit date.
						TimePeriod billingPeriod=calculateBillingPeriod(product,limitDate);
						logger.info("Current BillingPeriod for Product '{}': [{} - {}]'",product.getId(),billingPeriod.getStartDateTime(), billingPeriod.getEndDateTime());
					
					}catch(IllegalArgumentException e) {
						logger.error(e.getMessage());
						logger.error("Product '{}' skipped",product.getId());
						return;
					
					}catch (BillingSchedulerValidationException e) {
						logger.error(e.getMessage());
						logger.error("Product '{}' skipped",product.getId());
						return;
					
					}catch (ApiException e) {
						logger.error(e.getMessage());
						logger.error("Product '{}' skipped",product.getId());
						return;
					}
				}
				);	
		}
	}
	
	/*
	 * Method to calculate the latest billingPeriod of a Product that falls within the limit date.
	 * The last billingPeriod is calculated considering all the billingPeriod end dates of its ProductPrice(s)
	 */
	private TimePeriod calculateBillingPeriod(@NotNull Product product, @NotNull OffsetDateTime limitDate) throws IllegalArgumentException, ApiException, BillingSchedulerValidationException {

		logger.info("Calculation of current billingPeriod for Product '{}'", product.getId());
			
		TimePeriod currentBillingPeriod=null;
		
		// Get ProductPrice from Product
		List<ProductPrice> productPrices=product.getProductPrice();
		logger.debug("...ProductPrice(s) found: {}", productPrices.size());
		
		//Variable to collect, for all the ProductPrice of the Product, all the end dates of the billingPeriod(s) 
		Set<OffsetDateTime> billingPeriodsEndDates=new HashSet<OffsetDateTime>();
		
		// Get billingPeriod end dates for each ProductPrice
		for(ProductPrice pp: productPrices) {
			ProductOfferingPrice pop= ProductOfferingPriceUtils.getProductOfferingPrice(pp, productCatalogManagementApis);
			
			// Validate POP
			tmfEntityValidator.validateProductOfferingPrice(pop);
	
			List<OffsetDateTime> popBillingPeriodEndDates= billCycleService.calculateBillingPeriodEndDates(pop, product.getStartDate(), limitDate);

			billingPeriodsEndDates.addAll(popBillingPeriodEndDates);
		}
		
		if(!billingPeriodsEndDates.isEmpty())
			// Get all billingPeriods for the Product from the calculated billingPeriod end dates of the ProductPrice(s)
			billingPeriods= billCycleService.calculateBillingPeriods(new ArrayList<OffsetDateTime>(billingPeriodsEndDates), product.getStartDate());
		
		if(!billingPeriods.isEmpty()) {
			logger.debug("billingPeriod(s) for Product '{}'",product.getId());
			for(TimePeriod tp: billingPeriods) {
				logger.debug("[{} - {}]", tp.getStartDateTime(), tp.getEndDateTime());
			}
		
			// Get the latest billingPeriod of the Product (i.e., the last one)
			currentBillingPeriod=billingPeriods.get(billingPeriods.size()-1);
			
		}else {
			logger.debug("No billingPeriod(s) found for Product '{}'", product.getId());
		}
		
		return currentBillingPeriod;
	}

	
	/*
	 * TODO: Method not implemented yet. BillCycleSpecification not supported yet.
	 * @param now 
	 */
	private void calculateBillCycleFromBillCycleSpecs(OffsetDateTime now) {
		logger.info("Calculation of BillCycle at: {}", now);
		throw new UnsupportedOperationException("Method not supported yet!");
	}
	
	
	
}