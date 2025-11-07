package it.eng.dome.billing.scheduler.validator;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.eng.dome.billing.scheduler.exception.BillingSchedulerValidationException;
import it.eng.dome.billing.scheduler.utils.ProductOfferingPriceUtils;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import jakarta.validation.constraints.NotNull;

@Component
public class TMFEntityValidator {
	
	private final static Logger logger=LoggerFactory.getLogger(TMFEntityValidator.class);
	
	public void validateProductOfferingPrice(@NotNull ProductOfferingPrice pop) throws BillingSchedulerValidationException {
		
		List<ValidationIssue> issues=new ArrayList<ValidationIssue>();
		
		if(pop.getLifecycleStatus()==null || pop.getLifecycleStatus().isEmpty()) {
			String msg=String.format("The ProductOfferingPrice '%s' must have 'lifecycleStatus'", pop.getId());
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		if(pop.getIsBundle()==null){
			String msg=String.format("The ProductOfferingPrice '%s' must have 'isBundle'", pop.getId());
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		if((pop.getIsBundle()!=null && pop.getIsBundle()) && (pop.getBundledPopRelationship()==null || pop.getBundledPopRelationship().isEmpty())){
			String msg=String.format("The ProductOfferingPrice '%s' is bundled but the BundledPopRelationship are missing", pop.getId());
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		if((pop.getIsBundle()!=null && !pop.getIsBundle()) && (pop.getPriceType()==null || pop.getPriceType().isEmpty())) {
			String msg=String.format("The ProductOfferingPrice '%s' (not bundled) must have 'priceType'", pop.getId());
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		if(ProductOfferingPriceUtils.isPriceTypeRecurring(pop)) {
			if(pop.getRecurringChargePeriodLength()==null){
				String msg=String.format("The ProductOfferingPrice '%s' (recurring) must have 'recurringChargePeriodLength'", pop.getId());
				issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
			}
			if(pop.getRecurringChargePeriodType()==null || pop.getRecurringChargePeriodType().isEmpty()){
				String msg=String.format("The ProductOfferingPrice '%s' (recurring) must have 'recurringChargePeriodType'", pop.getId());
				issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
			}
		}
		
		if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssueSeverity.ERROR)) {
            throw new BillingSchedulerValidationException(issues);
        }
		
		logger.debug("Validation of ProductOfferingPrice {} successful", pop.getId());
		
	}
	
	public void validateProduct(@NotNull Product prod) throws BillingSchedulerValidationException {
		
		List<ValidationIssue> issues=new ArrayList<ValidationIssue>();
		
		if(prod.getProductPrice()==null || prod.getProductPrice().isEmpty()) {
			String msg=String.format("The Product '%s' must have 'ProductPrice'", prod.getId());
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		if(prod.getStartDate()==null){
			String msg=String.format("The Product '%s' must have 'startDate'", prod.getId());
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		
		if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssueSeverity.ERROR)) {
            throw new BillingSchedulerValidationException(issues);
        }
		
		logger.debug("Validation of Product {} successful", prod.getId());
		
	}	
	

}
