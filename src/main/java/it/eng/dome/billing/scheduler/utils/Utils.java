package it.eng.dome.billing.scheduler.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Utils {
	
	/**
	 * BillingPriceType enum provide a normalize priceType string 
	 * 1. assign recurring and recurring-postpaid as recurring
	 * 2. filter for only priceType = recurring, recurring-prepaid, recurring-postpaid, pay-per-use
	 * 3. normalize - accept: empty space, capital case and provide a string with "-" if need 
	 */
	public enum BillingPriceType {
	    RECURRING("recurring"),
	    RECURRING_PREPAID("recurring-prepaid"),
	    RECURRING_POSTPAID("recurring"),
	    PAY_PER_USE("pay-per-use");

	    private final String normalizedKey;

	    BillingPriceType(String normalizedKey) {
	        this.normalizedKey = normalizedKey;
	    }

	    public String getNormalizedKey() {
	        return normalizedKey;
	    }

	    public static String normalize(String input) {
	    	if (input == null) return null;
	        String normalizedInput = input
	            .toLowerCase()
	            .trim()
	            .replaceAll("\\s+", "-")
	            .toUpperCase()
	            .replace("-", "_");
	        
	        for (BillingPriceType type : values()) {
	            if (type.name().equalsIgnoreCase(normalizedInput)) {
	                return type.getNormalizedKey();
	            }
	        }
	        return null;
	    }
	    
	    public static String getAllowedValues() {
	    	return Arrays.stream(BillingPriceType.values())
	    		      .map(BillingPriceType::getNormalizedKey)
	    		      .distinct()
	    		      .collect(Collectors.joining(", "));
	    }
	}

}
