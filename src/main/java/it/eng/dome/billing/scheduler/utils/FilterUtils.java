package it.eng.dome.billing.scheduler.utils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

public class FilterUtils {
	
	/**
	 * Builds a filter map for CustomerBill queries based on day-level matching.
	 * <p>This method generates query parameters to filter CustomerBill resources
	 * by comparing the date portion (year, month, day) of the following fields:
	 * <ul>
	 *     <li>billDate</li>
	 *     <li>billingPeriod.startDateTime</li>
	 *     <li>billingPeriod.endDateTime</li>
	 * </ul>
	 *
	 * <p>Each date-time field is converted into a UTC day range
	 * [start of day, start of next day) to ignore time and timezone differences.
	 *
	 * @param cb the {@link CustomerBill} containing the reference date-time values
	 * @return a map of query parameters suitable for TMForum API filtering
	 */
	public static Map<String, String> buildCustomerBillFilter(CustomerBill cb) {

	    Map<String, String> filter = new HashMap<>();
	    addDayFilter(filter, "billDate", cb.getBillDate());
	    addDayFilter(filter, "billingPeriod.startDateTime",cb.getBillingPeriod().getStartDateTime());
	    addDayFilter(filter, "billingPeriod.endDateTime",cb.getBillingPeriod().getEndDateTime());

	    return filter;
	}
	
	private static void addDayFilter(Map<String, String> filter,
            String field,
            OffsetDateTime dateTime) {

		if (dateTime == null) return;

		OffsetDateTime startOfDay = dateTime.toLocalDate()
		.atStartOfDay()
		.atOffset(ZoneOffset.UTC);
		
		OffsetDateTime endOfDay = startOfDay.plusDays(1);
		
		filter.put(field + ".gte", startOfDay.toInstant().toString());
		filter.put(field + ".lt", endOfDay.toInstant().toString());
	}

}
