package it.eng.dome.billing.scheduler.model;

import com.fasterxml.jackson.annotation.JsonInclude;


//FIXME:Remove this class if moved in brokerage-utils
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillCycleSpecification {

    // Type and length to specify the duration of the bill cycle. From here, an initial billing period is computed on the product activation day.
    private Integer billingPeriodLength;
	private RecurringPeriod billingPeriodType;
	
    // This is to modify the end of the billing cycle, computed using the above two. 
    // Currently supported: COMPUTED_DAY, LAST_DAY_OF_CALENDAR_MONTH
    // Alternative values are NEXT_FRIDAY, NEXT_MONDAY, 15_OF_CALENDAR_MONTH, ..."
    private String billingPeriodEnd;

    // When the bill is computed and issued. Offset from the end of the billing cycle "
    private Integer billingDateShift;
 
    // When the bill is expected to be paid. Offset from the billDate
    private Integer paymentDueDateOffset;
    
	public Integer getBillingPeriodLength() {
        return billingPeriodLength;
    }

    public void setBillingPeriodLength(Integer billingPeriodLength) {
        this.billingPeriodLength = billingPeriodLength;
    }

    public RecurringPeriod getBillingPeriodType() {
        return billingPeriodType;
    }

    public void setBillingPeriodType(RecurringPeriod billingPeriodType) {
        this.billingPeriodType = billingPeriodType;
    }

    public String getBillingPeriodEnd() {
        return billingPeriodEnd;
    }

    public void setBillingPeriodEnd(String billingPeriodEnd) {
        this.billingPeriodEnd = billingPeriodEnd;
    }

    public Integer getBillingDateShift() {
        return billingDateShift;
    }

    public void setBillingDateShift(Integer billingDateShift) {
        this.billingDateShift = billingDateShift;
    }

    public Integer getPaymentDueDateOffset() {
        return paymentDueDateOffset;
    }

    public void setPaymentDueDateOffset(Integer paymentDueDateOffset) {
        this.paymentDueDateOffset = paymentDueDateOffset;
    }

}
