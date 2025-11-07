package it.eng.dome.billing.scheduler.model;

public enum PriceType {

	/**
	 * One off priceType
	 */
    ONE_TIME,
    /**
     * Recurring priceType prepaid 
     */
    RECURRING_PREPAID,
    /**
     * Recurring priceType postpaid. 
     * Pay-per-Use use cases are RECURRING_POSTPAID by default. 
     */
    RECURRING_POSTPAID,
    
    /**
     * Custom priceType defined by the Provider
     */
    CUSTOM
}