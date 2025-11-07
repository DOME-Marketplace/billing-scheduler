package it.eng.dome.billing.scheduler.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecurringChargePeriod {
	
	RecurringPeriod recurringChargePeriodType;
	Integer recurringChargePeriodLenght;
	
	public RecurringPeriod getRecurringChargePeriodType() {
		return recurringChargePeriodType;
	}
	public void setRecurringChargePeriodType(RecurringPeriod recurringChargePeriodType) {
		this.recurringChargePeriodType = recurringChargePeriodType;
	}
	public Integer getRecurringChargePeriodLenght() {
		return recurringChargePeriodLenght;
	}
	public void setRecurringChargePeriodLenght(Integer recrringChargePeriodLenght) {
		this.recurringChargePeriodLenght = recrringChargePeriodLenght;
	}

}
