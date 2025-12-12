package it.eng.dome.billing.scheduler.validator;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.eng.dome.billing.scheduler.exception.BillingSchedulerValidationException;
import it.eng.dome.brokerage.billing.utils.ProductOfferingPriceUtils;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import jakarta.validation.constraints.NotNull;

@Component
public class TMFEntityValidator {
	
	private final static Logger logger=LoggerFactory.getLogger(TMFEntityValidator.class);
	
	/**
	 * Validates the {@link ProductOfferingPrice}
	 * 
	 * @param pop {@link ProductOfferingPrice} to validate
	 * @throws BillingSchedulerValidationException if some unexpected/missing values are find
	 */
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
	
	/**
	 * Validates the {@link Product}
	 * 
	 * @param prod the {@link Product} to validate
	 * @throws BillingSchedulerValidationException if some unexpected/missing values are find
	 */
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
	
	/**
	 * Validate a {@link ProductPrice}
	 * 
	 * @param prodPrice the {@link ProductPrice} to validate
	 * @param prodId the identifier of the {@link Product} to which the ProductPrice belongs to
	 * @throws BillingEngineValidationException if some unexpected/missing values are find
	 */
	public void validateProductPrice(@NotNull ProductPrice prodPrice, @NotNull String prodId) throws BillingSchedulerValidationException{
		
		List<ValidationIssue> issues=new ArrayList<ValidationIssue>();
		
		if(prodPrice.getProductOfferingPrice()==null) {
			String msg=String.format("The ProductPrice of Product %s must have 'ProductOfferingPrice'", prodId);
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		if(prodPrice.getProductOfferingPrice().getId()==null || prodPrice.getProductOfferingPrice().getId().isEmpty()) {
			String msg=String.format("The ProductPrice f Product %s must have a 'ProductOfferingPrice' with a valorised 'id'", prodId);
			issues.add(new ValidationIssue(msg,ValidationIssueSeverity.ERROR));
		}
		
		this.throwsErrorValidationIssuesIfAny(issues);
		
		logger.debug("Validation of ProductPrice successful");
		
	}
	
	private void throwsErrorValidationIssuesIfAny(List<ValidationIssue> issues) throws BillingSchedulerValidationException {
		if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssueSeverity.ERROR)) {
	           throw new BillingSchedulerValidationException(issues);
	        }
	}
	

}
