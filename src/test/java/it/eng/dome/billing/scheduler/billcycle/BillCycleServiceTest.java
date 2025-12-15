package it.eng.dome.billing.scheduler.billcycle;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.eng.dome.billing.scheduler.service.BillCycleService;
import it.eng.dome.brokerage.model.RecurringChargePeriod;
import it.eng.dome.brokerage.model.RecurringPeriod;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class BillCycleServiceTest {
	
	private BillCycleService billCycleService;
	
	public BillCycleServiceTest() {
		billCycleService = new BillCycleService();
	}
	
	public static void main(String[] args) {
		BillCycleServiceTest test=new BillCycleServiceTest();
		
		Set<OffsetDateTime> endDays=new HashSet<OffsetDateTime>();
		
		// test calculation of billingPeriods end date by DAY
		endDays.addAll(test.testDAYPeriodType());
		// test calculation of billingPeriods end date by WEEK
		endDays.addAll(test.testWEEKPeriodType());
		// test calculation of billingPeriods end date by MONTH
		endDays.addAll(test.testMONTHPeriodType());
		// test calculation of billingPeriods end date by YEAR
		endDays.addAll(test.testYEARPeriodType());
		
		//Test calculation of billingPeriods from a lisy of billingPriod end date and an activation date
		List<OffsetDateTime> endDaysList=new ArrayList<OffsetDateTime>(endDays);

		Collections.sort(endDaysList);
		System.out.println("All end dates "+endDaysList);
		
		LocalDate date=LocalDate.of(2025, 9, 1);
		OffsetDateTime activationDate=date.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		List<TimePeriod> billingPeriods=test.getBillCycleService().calculateBillingPeriods(endDaysList, activationDate);
		
		for(TimePeriod tp: billingPeriods) {
			System.out.println("["+tp.getStartDateTime()+"-"+tp.getEndDateTime()+"]");
		}
		
		// Test isBllDate in a billingPeriod
		LocalDate date1=LocalDate.of(2025, 9, 1);
		OffsetDateTime startDate=date1.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		LocalDate date2=LocalDate.of(2025, 9, 5);
		OffsetDateTime endDate=date2.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		TimePeriod tp=new TimePeriod();
		tp.setStartDateTime(startDate);
		tp.endDateTime(endDate);
		
		LocalDate date3=LocalDate.of(2025, 8, 31);
		OffsetDateTime billDate=date3.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		boolean result=test.testBillDateWithinTimePeriod(billDate, tp);
		System.out.println("BillDate "+billDate+" in billingPeriod ["+tp.getStartDateTime()+"-"+tp.getEndDateTime()+"]: "+result);
		
	}
	
	public List<OffsetDateTime> testDAYPeriodType() {
		
		LocalDate date=LocalDate.of(2025, 9, 1);
		OffsetDateTime activationDate=date.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		LocalDate date2=LocalDate.of(2025, 10, 03);
		OffsetDateTime upTo=date2.atStartOfDay().atOffset(ZoneOffset.UTC);
		
		RecurringChargePeriod rcp=new RecurringChargePeriod(RecurringPeriod.DAY,5);
		
		return billCycleService.calculateBillingPeriodEndDates(rcp, activationDate, upTo);
		
	}
	
	public List<OffsetDateTime> testWEEKPeriodType() {
		
		LocalDate date=LocalDate.of(2025, 9, 1);
		OffsetDateTime activationDate=date.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		LocalDate date2=LocalDate.of(2025, 10, 03);
		OffsetDateTime upTo=date2.atStartOfDay().atOffset(ZoneOffset.UTC);
		
		RecurringChargePeriod rcp=new RecurringChargePeriod(RecurringPeriod.WEEK,2);
				
		return billCycleService.calculateBillingPeriodEndDates(rcp, activationDate, upTo);
	}
	
	public List<OffsetDateTime> testMONTHPeriodType() {
		
		LocalDate date=LocalDate.of(2025, 9, 1);
		OffsetDateTime activationDate=date.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		LocalDate date2=LocalDate.of(2026, 10, 03);
		OffsetDateTime upTo=date2.atStartOfDay().atOffset(ZoneOffset.UTC);
		
		RecurringChargePeriod rcp=new RecurringChargePeriod(RecurringPeriod.MONTH,1);
				
		return billCycleService.calculateBillingPeriodEndDates(rcp, activationDate, upTo);
	}

	
	public List<OffsetDateTime> testYEARPeriodType() {
		
		LocalDate date=LocalDate.of(2025, 9, 1);
		OffsetDateTime activationDate=date.atStartOfDay().atOffset(ZoneOffset.UTC);	
		
		LocalDate date2=LocalDate.of(2028, 10, 03);
		OffsetDateTime upTo=date2.atStartOfDay().atOffset(ZoneOffset.UTC);
		
		RecurringChargePeriod rcp=new RecurringChargePeriod(RecurringPeriod.YEAR,1);
		return billCycleService.calculateBillingPeriodEndDates(rcp, activationDate, upTo);
	}
	
	public boolean testBillDateWithinTimePeriod(OffsetDateTime date, TimePeriod billngPeriod) {
		return billCycleService.isBillDateWithinBillingPeriod(date, billngPeriod);
	}

	public BillCycleService getBillCycleService() {
		return billCycleService;
	}
	
}
