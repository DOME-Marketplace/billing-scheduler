package it.eng.dome.billing.scheduler.service;

import java.time.OffsetDateTime;

public class BillingUtils {
	

	protected static OffsetDateTime getNextBillingTime(OffsetDateTime t, OffsetDateTime now, String s) {
		String[] data = s.split("\\s+");
		if (data.length == 2) {
			if (data[0].matches("-?\\d+")) { // if data[0] is a number
				return nextBillingTime(t, now, Integer.parseInt(data[0]),  data[1]);
			}
		}else if (data.length == 1) {
			return nextBillingTime(t, now, 1, data[0]);
		}		
		return null;
	}
	
	protected static OffsetDateTime getPreviousBillingTime(OffsetDateTime t, String s) {
		String[] data = s.split("\\s+");
		if (data.length == 2) {
			if (data[0].matches("-?\\d+")) { // if data[0] is a number
				return getPreviusBilling(t, Integer.parseInt(data[0]),  data[1]);
			}
		}else if (data.length == 1) {
			return getPreviusBilling(t, 1, data[0]);
		}		
		return null;
	}
	
	private static OffsetDateTime nextBillingTime(OffsetDateTime time, OffsetDateTime now, int number, String unit) {
		if ((time.toLocalDate().equals(now.toLocalDate()) || (time.isAfter(now)))) {
			return time;
		}else {
		
			switch (unit) {
				case "day":
		        case "days":
		        case "daily":
		            return nextBillingTime(time.plusDays(number), now, number, unit);
		        case "week":
		        case "weeks":
		        case "weekly":
		            return nextBillingTime(time.plusWeeks(number), now, number, unit);
		        case "month":
		        case "months":
		        case "monthly":
		            return nextBillingTime(time.plusMonths(number), now, number, unit);
		        case "year":
		        case "years":
		            return nextBillingTime(time.plusYears(number), now, number, unit);
		        default:
		            return null;
		    }
		}
	}
	
	private static OffsetDateTime getPreviusBilling(OffsetDateTime time, int number, String unit) {
		switch (unit) {
			case "day":
	        case "days":
	        case "daily":
	            return time.minusDays(number);
	        case "week":
	        case "weeks":
	        case "weekly":
	            return time.minusWeeks(number);
	        case "month":
	        case "months":
	        case "monthly":
	            return time.minusMonths(number);
	        case "year":
	        case "years":
	            return time.minusYears(number); 
	        default:
	            return null;
	    }
	}
}
