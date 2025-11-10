package it.eng.dome.billing.scheduler.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.billing.scheduler.exception.BillingSchedulerValidationException;
import it.eng.dome.billing.scheduler.model.BillCycleSpecification;
import it.eng.dome.billing.scheduler.model.PriceType;
import it.eng.dome.billing.scheduler.model.RecurringChargePeriod;
import it.eng.dome.billing.scheduler.model.RecurringPeriod;
import it.eng.dome.billing.scheduler.tmf.TmfApiFactory;
import it.eng.dome.billing.scheduler.utils.ProductOfferingPriceUtils;
import it.eng.dome.billing.scheduler.validator.TMFEntityValidator;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.tmforum.tmf620.v4.ApiException;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.validation.constraints.NotNull;

@Service
public class BillCycleService implements InitializingBean{
	
	private final static Logger Logger = LoggerFactory.getLogger(BillCycleService.class);
	
	@Autowired
	private TmfApiFactory tmfApiFactory;
	
	private ProductCatalogManagementApis productCatalogManagementApis;
	
	@Autowired
	private TMFEntityValidator tmfEntityValidator;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		productCatalogManagementApis = new ProductCatalogManagementApis(tmfApiFactory.getTMF620ProductCatalogApiClient());
	}


	/**
	 * Calculates all the billingPeriod END dates of the BillCycle, included from an activation date and a limit date, according to a {@link BillCycleSpecification}. 
	 * 
	 * @param billCycleSpec A {@link BillCycleSpecification} instance which specify the billingPeriodType and billingPeriodLength
	 * @param activationDate A start date from which the billingPeriod end dates are calculated
	 * @param limitDate A limit date to stop the calculation of billingPeriod end dates
	 * @return The list of all billingPeriod END dates of the BillCycle that fall between the activation and limit dates
	 * @throws IllegalArgumentException If the {@link BillCycleSpecification} containes unexpected values
	 */
	public List<OffsetDateTime> calculateBillingPeriodEndDates(@NotNull BillCycleSpecification billCycleSpec, @NotNull OffsetDateTime activationDate, @NotNull OffsetDateTime limitDate) throws IllegalArgumentException{
		
		Logger.info("Starting calculation of the billingPeriod end dates for BillCycleSpecification with recurring period '{}' and lenght '{}' and activation date '{}'",
			billCycleSpec.getBillingPeriodType(),billCycleSpec.getBillingPeriodLength(), activationDate);
		
		
		RecurringPeriod billingPeriodType=billCycleSpec.getBillingPeriodType();
		Integer billingPeriodLength=billCycleSpec.getBillingPeriodLength();
		
		if(billingPeriodType!=null && billingPeriodLength!=null && billingPeriodLength>0) {
			Stream<OffsetDateTime> streamData=Stream.empty();
			

			switch (billCycleSpec.getBillingPeriodType()) {
			case DAY: {
			
				// Stream of dates every n DAY according to the BillCycleSpecification (the activation date is included)
				//Stream<OffsetDateTime> streamPerDay = Stream.iterate(
				streamData = Stream.iterate(
						activationDate.plusDays( billCycleSpec.getBillingPeriodLength()- 1),          
						d -> d.plusDays(billCycleSpec.getBillingPeriodLength())                    
						);
	       
		       //billPeriodEndDates=streamPerDay.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //Logger.info("Per DAYs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			case WEEK: {
			
				// Stream of dates every n WEEK according to the BillCycleSpecification (the activation date is included)
		        //Stream<OffsetDateTime> streamPerWeek = Stream.iterate(
				streamData = Stream.iterate(
		        		activationDate.plusDays((7 * billCycleSpec.getBillingPeriodLength())-1),          
		                d -> d.plusDays(7 * billCycleSpec.getBillingPeriodLength())                    
		        );
		       
		       //billPeriodEndDates=streamPerWeek.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //Logger.info("Per WEEKs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			case MONTH: {
				// Stream of dates every n MONTH according to the BillCycleSpecification (the activation date is included)
				//Stream<OffsetDateTime> streamPerMonth = Stream.iterate(
				streamData = Stream.iterate(
				        1, i -> i + 1
				).map(i -> activationDate.plusMonths(i * billCycleSpec.getBillingPeriodLength()).minusDays(1));
		       
		       //billPeriodEndDates=streamPerMonth.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //Logger.info("Per MONTHs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			case YEAR: {
				// Stream of dates every n YEAR according to the BillCycleSpecification (the activation date is included)
		        //Stream<OffsetDateTime> streamPerYear = Stream.iterate(
				streamData = Stream.iterate(
				        1, i -> i + 1
				).map(i -> activationDate.plusYears(i * billCycleSpec.getBillingPeriodLength()).minusDays(1));
		       
		       
		      // billPeriodEndDates=streamPerYear.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //Logger.info("Per YEARs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			default:
				throw new IllegalArgumentException("Error in the BillCycleSpecification: unexpected value for billingPeriodType");
			}
			
			List<OffsetDateTime> billPeriodEndDates = streamData.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
			if(activationDate.isAfter(limitDate))
		    	   Logger.warn("activationDate '{}' is after limitDate '{}'", activationDate, limitDate);
		    Logger.info("Per {} billingPeriod END dates:{}",billingPeriodType,billPeriodEndDates);
		    
		    return billPeriodEndDates;
			
		}else {
			throw new IllegalArgumentException("Error in the BillCycleSpecification: billingPeriodType must not be null, billingPeriodLength must be greater than 0");
		}
		
		
		
	    //return billPeriodEndDates;

	}
	
	/**
	 * Calculates all the billingPeriod END dates of the BillCycle, included from an activation date and a limit date, according to the specified {@link RecurringChargePeriod} (e.g., 5 DAY, 2 WEEK; 1 MONTH, 1 YEAR) 
	 * 
	 * @param recurringChargePeriod A {@link RecurringChargePeriod} specifying the recurringChargePeriodType and recurringChargePeriodLength  
	 * @param activationDate A start date from which the billingPeriod end dates are calculated
	 * @param limitDate A limit date to stop the calculation of billingPeriod end dates
	 * @return The list of all billingPeriod END dates of the BillCycle that fall between the activation and limit dates
	 * @throws IllegalArgumentException If the {@link RecurringChargePeriod} containes unexpected values
	 */
	public List<OffsetDateTime> calculateBillingPeriodEndDates(@NotNull RecurringChargePeriod recurringChargePeriod, @NotNull OffsetDateTime activationDate, @NotNull OffsetDateTime limitDate) throws IllegalArgumentException{
		
		Logger.info("Calculation of the billingPeriod end dates for recurringPeriodLenght '{}' and recurringPeriodType '{}' and activation date '{}'",
				recurringChargePeriod.getRecurringChargePeriodLenght(),recurringChargePeriod.getRecurringChargePeriodType(), activationDate);
		
		RecurringPeriod billingPeriodType=recurringChargePeriod.getRecurringChargePeriodType();
		Integer billingPeriodLength=recurringChargePeriod.getRecurringChargePeriodLenght();
		
		if(billingPeriodType!=null && billingPeriodLength!=null && billingPeriodLength>0) {
			Stream<OffsetDateTime> streamData=Stream.empty();
			
			switch (recurringChargePeriod.getRecurringChargePeriodType()) {
			case DAY: {
				
				// Stream of dates every n DAY according to the RecurringChargePeriod (the activation date is included)
		        //Stream<OffsetDateTime> streamPerDay = Stream.iterate(
				streamData = Stream.iterate(
		                activationDate.plusDays(recurringChargePeriod.getRecurringChargePeriodLenght()- 1),          
		                d -> d.plusDays(recurringChargePeriod.getRecurringChargePeriodLenght())                    
		        );
		       
		       //billPeriodEndDates=streamPerDay.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //if(activationDate.isAfter(limitDate))
		    	 //  Logger.warn("activationDate '{}' is after limitDate '{}'", activationDate, limitDate);
		       //Logger.info("Per DAYs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			case WEEK: {
				
				// Stream of dates every n WEEK according to the RecurringChargePeriod (the activation date is included)
		        //Stream<OffsetDateTime> streamPerWeek = Stream.iterate(
				streamData = Stream.iterate(
		        		activationDate.plusDays((7 * recurringChargePeriod.getRecurringChargePeriodLenght())-1),          
		                d -> d.plusDays(7 * recurringChargePeriod.getRecurringChargePeriodLenght())                    
		        );
		       
		       //billPeriodEndDates=streamPerWeek.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //Logger.info("Per WEEKs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			case MONTH: {
				// Stream of dates every n MONTH according to the RecurringChargePeriod (the activation date is included)
				//Stream<OffsetDateTime> streamPerMonth = Stream.iterate(
				streamData = Stream.iterate(
				        1, i -> i + 1
				).map(i -> activationDate.plusMonths(i * recurringChargePeriod.getRecurringChargePeriodLenght()).minusDays(1));
		       
		       //billPeriodEndDates=streamPerMonth.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //Logger.info("Per MONTHs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			case YEAR: {
				// Stream of dates every n YEAR according to the RecurringChargePeriod (the activation date is included)
		        //Stream<OffsetDateTime> streamPerYear = Stream.iterate(
				streamData = Stream.iterate(
				        1, i -> i + 1
				).map(i -> activationDate.plusYears(i * recurringChargePeriod.getRecurringChargePeriodLenght()).minusDays(1));
		       
		       
		       //billPeriodEndDates=streamPerYear.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
		       //Logger.info("Per YEARs billingPeriod END dates:{}",billPeriodEndDates);
		       break;
			}
			default:
				throw new IllegalArgumentException("Error in the RecurringChargePeriod: unexpected value for billingPeriodType");
			}
			
			List<OffsetDateTime> billPeriodEndDates = streamData.takeWhile(d -> d.isBefore(limitDate) || d.isEqual(limitDate)).toList();
			if(activationDate.isAfter(limitDate))
		    	   Logger.warn("activationDate '{}' is after limitDate '{}'", activationDate, limitDate);
		    Logger.info("Per {} billingPeriod END dates:{}",billingPeriodType,billPeriodEndDates);
		    
		    return billPeriodEndDates;
			
			
		}else {
			throw new IllegalArgumentException("Error in the RecurringChargePeriod: billingPeriodType must not be null, billingPeriodLength must be greater than 0");
		}
			
	    //return billPeriodEndDates;

	}
	
	/**
	 * Calculates all the billingPeriods (i.e., startDate - endDate) of the BillCycle, considering a list of billingPeriod end dates and an initial activation date (e.g., activation date of a Product) 
	 *
	 * @param billingPeriodEndDates List of dates representing the end dates of the BillCycle
	 * @param activationDate An activation date from which the billingPeriod(s) are calculated
	 * @return A list of {@link TimePeriod} representing the billingPeriod(s) of the BillCycle
	 */
	public List<TimePeriod> calculateBillingPeriods(@NotNull List<OffsetDateTime> billingPeriodEndDates, @NotNull OffsetDateTime activationDate){
			
		Logger.info("Calculation of the billingPeriods from activationDate {}",activationDate);
		
		List<TimePeriod> billingPeriods=new ArrayList<TimePeriod>();
		
		// Sort dates
		Collections.sort(billingPeriodEndDates);
		
		OffsetDateTime startDate=activationDate;
		
		for(OffsetDateTime endDate: billingPeriodEndDates) {
			TimePeriod tp=new TimePeriod();
			tp.setStartDateTime(startDate);
			tp.setEndDateTime(endDate);
			
			billingPeriods.add(tp);
			
			startDate=endDate.plusDays(1);
		}
		
		return billingPeriods;
	}
	
	/**
	 * Checks if a bill date falls within a billingPeriod
	 * 
	 * @param billingDate A bill date to check
	 * @param billingPeriod A {@link TimePeriod} 
	 * @return true if the bill date falls within the billingPeriod, false otherwise
	 */
	public boolean isBillDateWithinBillingPeriod(@NotNull OffsetDateTime billingDate, @NotNull TimePeriod billingPeriod) {
		return (!billingDate.isBefore(billingPeriod.getStartDateTime())) && (!billingDate.isAfter(billingPeriod.getEndDateTime()));
	}
	
	/**
	 * Calculates all the billingPeriod END dates for a {@link ProductOfferingPrice} with {@link PriceType} RECURRING_PREPAID or RECURRING_POSTPAID, included from an activation date and a limit date, according to its {@link RecurringChargePeriod} 
	 * 
	 * @param pop A {@link ProductOfferingPrice} to calculate billingPeriod END dates according to its {@link RecurringChargePeriod}
	 * @param activationDate An activation date from which the billingPeriod end dates are calculated
	 * @param limitDate A limit date to stop the calculation of billingPeriod end dates
	 * @return The list of all billingPeriod END dates of the {@link ProductOfferingPrice} with {@link PriceType} RECURRING_PREPAID or RECURRING_POSTPAID that fall between the activation and limit dates
	 * @throws IllegalArgumentException If the {@link ProductOfferingPrice} refers to a not supported {@link PriceType}
	 */
	public List<OffsetDateTime> calculateBillingPeriodEndDates(@NotNull ProductOfferingPrice pop, @NotNull OffsetDateTime activationDate, @NotNull OffsetDateTime limitDate) throws IllegalArgumentException, ApiException, BillingSchedulerValidationException{
		Logger.info("Calculation of billingPeriod(s) end dates for ProductOfferingPrice '{}' from activationDate '{}'", pop.getId(), activationDate);
		
		List<OffsetDateTime> billPeriodEndDates=new ArrayList<OffsetDateTime>();
		
		if(pop.getIsBundle()) {
			Logger.debug("POP is bundled...calculation of billingPeriod end dates for pop relationships...");
			List<ProductOfferingPrice> popRels=ProductOfferingPriceUtils.getProductOfferingPrices(pop.getBundledPopRelationship(), productCatalogManagementApis);
			for(ProductOfferingPrice popRel:popRels) {
				
				tmfEntityValidator.validateProductOfferingPrice(pop);
				
				if(ProductOfferingPriceUtils.isPriceTypeOneTime(popRel)) {
					Logger.debug("POP relationship '{}' is ONE_TIME...skipped", popRel.getId());
					continue;
				}
				if(ProductOfferingPriceUtils.isPriceTypeRecurring(popRel)) {
					Logger.debug("POP relationship '{}' is RECURRING...", popRel.getId());
					List<OffsetDateTime> popRelEndDates=calculateBillingPeriodEndDates(ProductOfferingPriceUtils.getRecurringChargePeriod(popRel), activationDate, limitDate);
					billPeriodEndDates.addAll(popRelEndDates);
				}else {
					throw new IllegalArgumentException("Is not possible to calculate billingPeriod(s) for ProductOfferingPrice "+popRel.getId()+" - not supported priceType");
				}
			}
			
		}else {
			if(ProductOfferingPriceUtils.isPriceTypeRecurring(pop)) {
				Logger.debug("POP is RECURRING...");
				List<OffsetDateTime> popEndDates=calculateBillingPeriodEndDates(ProductOfferingPriceUtils.getRecurringChargePeriod(pop), activationDate, limitDate);
				billPeriodEndDates.addAll(popEndDates);
			}else if (ProductOfferingPriceUtils.isPriceTypeOneTime(pop)) {
				Logger.debug("POP is ONE_TIME...skipped");
				return billPeriodEndDates;
			}else {
				throw new IllegalArgumentException("Is not possible to calculate billingPeriod(s) for ProductOfferingPrice "+pop.getId()+" - not supported priceType");
			}
		}
		
		return billPeriodEndDates;
	}

}
