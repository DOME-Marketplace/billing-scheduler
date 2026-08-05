package it.eng.dome.billing.scheduler.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.model.BillCycleSpecification;
import it.eng.dome.brokerage.model.RecurringChargePeriod;
import it.eng.dome.brokerage.model.RecurringPeriod;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.validation.constraints.NotNull;

@Service
public class BillCycleService{
	
	private final static Logger Logger = LoggerFactory.getLogger(BillCycleService.class);

	/**
	 * Calculates all the billingPeriod END dates of the BillCycle, included from an activation date and a limit date, according to a {@link BillCycleSpecification}. 
	 * Billing periods are represented as semi-open intervals [startDate, endDate) where startDate is inclusive and endDate is exclusive.
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
				streamData = Stream.iterate(
						//activationDate.plusDays( billCycleSpec.getBillingPeriodLength()- 1),
						activationDate.plusDays( billCycleSpec.getBillingPeriodLength()),          
						d -> d.plusDays(billCycleSpec.getBillingPeriodLength())                    
						);
		       break;
			}
			case WEEK: {
			
				// Stream of dates every n WEEK according to the BillCycleSpecification (the activation date is included)
				streamData = Stream.iterate(
		        		//activationDate.plusDays((7 * billCycleSpec.getBillingPeriodLength())-1),
						activationDate.plusDays((7 * billCycleSpec.getBillingPeriodLength())), 
		                d -> d.plusDays(7 * billCycleSpec.getBillingPeriodLength())                    
		        );
		       break;
			}
			case MONTH: {
				// Stream of dates every n MONTH according to the BillCycleSpecification (the activation date is included)
				streamData = Stream.iterate(
				        1, i -> i + 1
				//).map(i -> activationDate.plusMonths(i * billCycleSpec.getBillingPeriodLength()).minusDays(1));
				).map(i -> activationDate.plusMonths(i * billCycleSpec.getBillingPeriodLength()));
		       break;
			}
			case YEAR: {
				// Stream of dates every n YEAR according to the BillCycleSpecification (the activation date is included)
				streamData = Stream.iterate(
				        1, i -> i + 1
				//).map(i -> activationDate.plusYears(i * billCycleSpec.getBillingPeriodLength()).minusDays(1));
				).map(i -> activationDate.plusYears(i * billCycleSpec.getBillingPeriodLength()));
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

	}
	/**
	 * Calculates the billingPeriod END dates of the BillCycle, included from an activation {@link OffsetDateTime} and a limit {@link OffsetDateTime}, according to the specified {@link RecurringChargePeriod} (e.g., 5 DAY, 2 WEEK; 1 MONTH, 1 YEAR) 
	 * Billing periods are represented as semi-open intervals [startDate, endDate) where startDate is inclusive and endDate is exclusive.
	 * 
	 * @param recurringChargePeriod A {@link RecurringChargePeriod} specifying the recurringChargePeriodType and recurringChargePeriodLength  
	 * @param activationDate An {@link OffsetDateTime} representing a start date from which the billingPeriod end dates are calculated
	 * @param limitDate An {@link OffsetDateTime} representing a limit date to stop the calculation of billingPeriod end dates
	 * @return The list of {@link OffsetDateTime} representing all the billingPeriod END dates of the BillCycle that fall between the activation and limit dates
	 * @throws IllegalArgumentException If the {@link RecurringChargePeriod} contains unexpected values
	 */
	public List<OffsetDateTime> calculateBillingPeriodEndDates(@NotNull RecurringChargePeriod recurringChargePeriod, @NotNull OffsetDateTime activationDate, @NotNull OffsetDateTime limitDate) throws IllegalArgumentException{
		
		Logger.debug("Calculation of the billingPeriod end dates for recurringPeriodLenght '{}' and recurringPeriodType '{}' and activation date '{}'",
				recurringChargePeriod.getRecurringChargePeriodLenght(),recurringChargePeriod.getRecurringChargePeriodType(), activationDate);
		
		List<OffsetDateTime> endDates=new ArrayList<OffsetDateTime>();
		
		RecurringPeriod billingPeriodType=recurringChargePeriod.getRecurringChargePeriodType();
		Integer billingPeriodLength=recurringChargePeriod.getRecurringChargePeriodLenght();
		

	    if (activationDate.isAfter(limitDate)) {
	        Logger.warn("activationDate '{}' is after limitDate '{}'", activationDate, limitDate);
	        return endDates;
	    }

	    if (billingPeriodType == null || billingPeriodLength == null || billingPeriodLength <= 0) {
	    	throw new IllegalArgumentException("Error in the RecurringChargePeriod: billingPeriodType must not be null, billingPeriodLength must be greater than 0");
	    }
			
		Stream<OffsetDateTime> streamData=Stream.empty();
		
		switch (recurringChargePeriod.getRecurringChargePeriodType()) {
		case DAY: {
			
			streamData = Stream.iterate(
	                //activationDate.plusDays(billingPeriodLength- 1),  
					activationDate.plusDays(billingPeriodLength), 
	                d -> d.plusDays(billingPeriodLength)                    
	        );
	       break;
		}
		case WEEK: {
			
			streamData = Stream.iterate(
	        		//activationDate.plusDays((7 * billingPeriodLength)-1),
					activationDate.plusDays((7 * billingPeriodLength)), 
	                d -> d.plusDays(7 * billingPeriodLength)                    
	        );
	       break;
		}
		case MONTH: {

			streamData = Stream.iterate(
			        1, i -> i + 1
			//).map(i -> activationDate.plusMonths(i * billingPeriodLength).minusDays(1));
			).map(i -> activationDate.plusMonths(i * billingPeriodLength));
	       break;
		}
		case YEAR: {

			streamData = Stream.iterate(
			        1, i -> i + 1
			//).map(i -> activationDate.plusYears(i * billingPeriodLength).minusDays(1));
			).map(i -> activationDate.plusYears(i * billingPeriodLength));
	       break;
		}
		default:
			throw new IllegalArgumentException("Error in the RecurringChargePeriod: unexpected value for billingPeriodType");
		}
		
		endDates=streamData
	            .takeWhile(d -> !d.isAfter(limitDate))
	            .toList(); // immutable → create new ArrayList
	    
		Logger.debug("Per {} {} billingPeriod END dates:{}",billingPeriodLength,billingPeriodType,endDates);
	    
	    return new ArrayList<>(endDates);
	}
	
	/**
	 * Calculates all the billingPeriods (i.e., startDate - endDate) of the BillCycle, considering a list of billingPeriod end dates and an initial activation date (e.g., activation date of a Product) 
	 * Billing periods are represented as semi-open intervals [startDate, endDate) where startDate is inclusive and endDate is exclusive.
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
		//return (!billingDate.isBefore(billingPeriod.getStartDateTime())) && (billingDate.isBefore(billingPeriod.getEndDateTime()));
	}

}
