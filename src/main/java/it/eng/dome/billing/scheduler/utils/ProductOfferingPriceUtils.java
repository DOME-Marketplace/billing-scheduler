package it.eng.dome.billing.scheduler.utils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.billing.scheduler.model.PriceType;
import it.eng.dome.billing.scheduler.model.RecurringChargePeriod;
import it.eng.dome.billing.scheduler.model.RecurringPeriod;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.tmforum.tmf620.v4.ApiException;
import it.eng.dome.tmforum.tmf620.v4.model.BundledProductOfferingPriceRelationship;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductOfferingPriceRef;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import jakarta.validation.constraints.NotNull;

public class ProductOfferingPriceUtils {
	
	private static Logger logger=LoggerFactory.getLogger(ProductOfferingPriceUtils.class);
	
	/**
	 * Retrieves the {@link ProductOfferingPrice} referenced in the {@link ProductPrice}
	 * @param pp A ProductPrice  
	 * @param popApis A {@link ProductCatalogManagementApis} instance
	 * @return The ProductOfferingPrice referenced by the ProductPrice
	 * @throws IllegalArgumentException If the reference to the ProductOfferingPrice is missing in the ProductPrice or the referenced ProductOfferingPrice does not exist  
	 * @throws ApiException If the API call to retrieve the ProductOfferingPrice fails or the resource cannot be retrieved
	 */
	public static ProductOfferingPrice getProductOfferingPrice(@NotNull ProductPrice pp, @NotNull ProductCatalogManagementApis popApis) throws IllegalArgumentException, ApiException {
		
		ProductOfferingPriceRef popRef = pp.getProductOfferingPrice();
		if(popRef==null) 
			throw new IllegalArgumentException("The ProductOfferingPriceRef is null in the ProductPrice");
		
		String fields="bundledPopRelationship,isBundle,lifecycleStatus,priceType,recurringChargePeriodLength,recurringChargePeriodType";
		ProductOfferingPrice pop=popApis.getProductOfferingPrice(popRef.getId(), fields);
		
		if(pop==null)			
			throw new IllegalArgumentException(String.format("The ProductOfferingPrice '%s' is null", popRef.getId()));
		
		return pop;
	}
	
	/**
	 * Retrieves the list of {@link ProductOfferingPrice} from a list of {@link BundledProductOfferingPriceRelationship}
	 *   
	 * @param popRelationships A list of BundledProductOfferingPriceRelationship referring the ProductOfferingPrice(s)
	 * @param popApis  A {@link ProductCatalogManagementApis} instance
	 * @return The list of {@link ProductOfferingPrice} referenced in the list of BundledProductOfferingPriceRelationship 
	 * @throws IllegalArgumentException If a ProductOfferingPrice referred to does not exist
	 * @throws ApiException If the API call to retrieve a ProductOfferingPrice fails or the resource cannot be retrieved
	 */
	public static List<ProductOfferingPrice> getProductOfferingPrices(@NotNull List<BundledProductOfferingPriceRelationship> popRelationships, @NotNull ProductCatalogManagementApis popApis) throws IllegalArgumentException, ApiException{
		logger.debug("Retrieving of ProductOfferingPrice relationships...");
		List<ProductOfferingPrice> popList=new ArrayList<ProductOfferingPrice>();
		
		for(BundledProductOfferingPriceRelationship popRel: popRelationships) {
			
			String fields="bundledPopRelationship,isBundle,lifecycleStatus,priceType,recurringChargePeriodLength,recurringChargePeriodType";
			ProductOfferingPrice pop = popApis.getProductOfferingPrice(popRel.getId(),fields);
			
			if(pop==null) {
				throw new IllegalArgumentException(String.format("The ProductOfferingPrice '%s' is null", popRel.getId()));
			}
			
			popList.add(pop);
		}
		
		return popList;
	}
	
	/**
	 * Retrieves the {@link RecurringChargePeriod} of a {@link ProductOfferingPrice}
	 *  
	 * @param pop The ProductOfferingPrice instance from which the RecurringChargePeriod must be retrieved
	 * @return The RecurringChargePeriod of the specified ProductOfferingPrice
	 * @throws IllegalArgumentException If the ProductOfferingPrice contains unexpected values
	 */
	public static RecurringChargePeriod getRecurringChargePeriod(@NotNull ProductOfferingPrice pop) throws IllegalArgumentException{
		RecurringChargePeriod rcp=new RecurringChargePeriod();
		
		Integer periodLenght=pop.getRecurringChargePeriodLength();
		RecurringPeriod periodType=getRecurringPeriodType(pop);
		
		if(periodLenght!=null && periodType!=null) { 
			if(periodLenght<=0)
				throw new IllegalArgumentException(String.format("The 'recurringChargePeriodLenght' must be greater than zero in the ProductOfferingPrice '%s'", pop.getId()));
			
			rcp.setRecurringChargePeriodLenght(periodLenght);
			rcp.setRecurringChargePeriodType(periodType);
			
			return rcp;
		}
		return null;
	}
	
	/**
	 * Retrieves the {@link RecurringPeriod} of a {@link ProductOfferingPrice}
	 * @param pop The ProductOfferingPrice from which the RecurringPeriod must be retrieved
	 * @return The RecurringPeriod of the specified ProductOfferingPrice
	 * @throws IllegalArgumentException If the ProductOfferingPrice contains unexpected values
	 */
	public static RecurringPeriod getRecurringPeriodType(@NotNull ProductOfferingPrice pop) throws IllegalArgumentException{
		 
		String popRecChargePeriodType=pop.getRecurringChargePeriodType();
		
		if(popRecChargePeriodType!=null) {
			String normalized = popRecChargePeriodType.toLowerCase().replaceAll("[\\s-]+", "_");
		
			switch (normalized) {
	         case "day":
	         case "days":
	         case "daily":
	             return RecurringPeriod.DAY;
	         case "week":
	         case "weeks":
	         case "weekly":
	             return RecurringPeriod.WEEK;
	         case "month":
	         case "months": 
	         case "monthly":
	             return RecurringPeriod.MONTH;
	         case "year":
	         case "years":
	         case "yearly.":
	             return RecurringPeriod.YEAR;
	         default:
	             throw new IllegalArgumentException(String.format("Invalid '%s' recurringChargePeriod in the ProductOfferingPrice '%s'", normalized,pop.getId()));
			 }
		}else {
			throw new IllegalArgumentException(String.format("Error in ProductOfferingPrice '%s': the RecurringChargePeriodType is null", pop.getId()));
		}
	}
	
	/**
	 * Retrieves the {@link PriceType} of a {@link ProductOfferingPrice}
	 * @param pop he ProductOfferingPrice from which the PriceType must be retrieved
	 * @return The PriceType of the specified ProductOfferingPrice
	 * @throws IllegalArgumentException If the ProductOfferingPrice contains unexpected values
	 */
	public static PriceType getPriceType(@NotNull ProductOfferingPrice pop) throws IllegalArgumentException{
		 
		String popPriceType=pop.getPriceType();

		if(popPriceType!=null) {
			String normalized = popPriceType.toLowerCase().replaceAll("[\\s-]+", "_");
			
			switch (normalized) {
	         case "onetime":
	         case "one_time":
	             return PriceType.ONE_TIME;
	         case "recurring_prepaid":
	         case "recurringprepaid":
	             return PriceType.RECURRING_PREPAID;
	         case "recurring":
	         case "recurring_postpaid": 
	         case "recurringpostpaid":
	         case "usage":
	         case "pay_per_use":
	         case "payperuse":
	             return PriceType.RECURRING_POSTPAID;
	         case "custom":
	             return PriceType.CUSTOM;
	         default:
	             throw new IllegalArgumentException(String.format("Invalid '%s' priceType in the ProductOfferingPrice '%s'", normalized, pop.getId()));
			 } 
		}else {
			throw new IllegalArgumentException(String.format("Error in ProductOfferingPrice '%s': the priceType is null", pop.getId()));
		}
	}
	
	/**
	 * Checks if the specified {@link ProductOfferingPrice} is a ONE_TIME {@link PriceType}
	 * @param pop A ProductOfferingPrice instance
	 * @return True if the specified ProductOfferingPrice is a ONE_TIME PriceType, false otherwise
	 */
	public static boolean isPriceTypeOneTime(@NotNull ProductOfferingPrice pop){
		if(getPriceType(pop)!=null)
			return getPriceType(pop).equals(PriceType.ONE_TIME);
		else
			return false;
	}
	
	/**
	 * Checks if the specified {@link ProductOfferingPrice} is a CUSTOM {@link PriceType}
	 * @param pop A ProductOfferingPrice instance
	 * @return True if the specified ProductOfferingPrice is a CUSTOM PriceType, false otherwise
	 */
	public static boolean isPriceTypeCustom(@NotNull ProductOfferingPrice pop) {
		if(getPriceType(pop)!=null)
			return getPriceType(pop).equals(PriceType.CUSTOM);
		else
			return false;
	}
	
	/**
	 * Checks if the specified {@link ProductOfferingPrice} is a RECURRING_POSTPAID or RECURRING_PREPAID {@link PriceType}
	 * @param pop A ProductOfferingPrice instance
	 * @return True if the specified ProductOfferingPrice is a RECURRING_POSTPAID or RECURRING_PREPAID PriceType, false otherwise
	 */
	public static boolean isPriceTypeRecurring(@NotNull ProductOfferingPrice pop) throws IllegalArgumentException{
		if(getPriceType(pop)!=null)
			return getPriceType(pop).equals(PriceType.RECURRING_POSTPAID) || getPriceType(pop).equals(PriceType.RECURRING_PREPAID);
		else
			return false;
	}

}
