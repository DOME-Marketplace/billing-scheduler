package it.eng.dome.billing.scheduler.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.billing.scheduler.exception.BadTmfDataException;
import it.eng.dome.billing.scheduler.exception.ExternalServiceException;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

@Service
public class TmfDataRetriever {

    private final Logger logger = LoggerFactory.getLogger(TmfDataRetriever.class);
  
    @Autowired
    private CustomerBillApis customerBillApis;
    
    @Autowired
    private AppliedCustomerBillRateApis appliedCustomerBillRateApis;

    /**
     * Fetches customer bills in batches from the TMF API based on the provided fields and filter.
     * @param fields the fields to retrieves
     * @param filter the filter
     * @param batchSize the batch size
     * @param consumer the consumer
     * @throws ExternalServiceException if an error occurs while fetching the CustomerBill(s)
     */

    public void fetchCustomerBills(String fields, Map<String, String> filter, int batchSize, Consumer<CustomerBill> consumer) throws ExternalServiceException {
        try {
            FetchUtils.fetchByBatch(
                    (FetchUtils.ListedFetcher<CustomerBill>) (f, flt, size, offset) ->
                            customerBillApis.listCustomerBills(f, flt, size, offset),
                    fields,
                    filter,
                    batchSize,
                    batch -> batch.forEach(consumer)
            );
        } catch (Exception e) {
            logger.error("Failed to fetch CustomerBills by batch", e);
            throw new ExternalServiceException("Failed to fetch CustomerBills by batch", e);
        }
    }
    
    // ======= TMF Customer Bill ========
    
    /**
	 * Retrieves a customer bill from the TMF API by its ID.
	 *
	 * @param customerBillId The ID of the customer bill to retrieve.
	 * @return The CustomerBill object if found, null otherwise.
	 * @throws BadTmfDataException If the provided customer bill ID is invalid.
	 * @throws ExternalServiceException If an error occurs while retrieving the customer bill.
	 */
    public CustomerBill getCustomerBill(String customerBillId)
            throws BadTmfDataException, ExternalServiceException {
        logger.debug("Retrieving Customer Bill from TMF API By Customer Bill with id: {}", customerBillId);

        if (customerBillId == null) {
            throw new BadTmfDataException("CustomerBill", customerBillId, "Customer Bill ID cannot be null");
        }

        try {
            CustomerBill cb = this.customerBillApis.getCustomerBill(customerBillId, null);

            if (cb == null) {
                logger.info("No Customer Bill found for Customer Bill with id {}: ", customerBillId);
                return null;
            }

            return cb;
        } catch (Exception e) {
            logger.error("Error retrieving Customer Bill {}: {}", customerBillId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve Customer Bill with ID: " + customerBillId, e);
        }
    }

    // ======= TMF AppliedCustomerBillingRate ========
    
    /**
	 * Retrieves an AppliedCustomerBillingRate from the TMF API by its ID.
	 *
	 * @param acbrId The ID of the appliedCustomerBillingRate to retrieve.
	 * @return The AppliedCustomerBillingRate object if found, null otherwise.
	 * @throws BadTmfDataException If the provided AppliedCustomerBillingRate ID is invalid.
	 * @throws ExternalServiceException If an error occurs while retrieving the appliedCustomerBillingRate.
	 */
	public AppliedCustomerBillingRate getACBR(String acbrId) throws BadTmfDataException, ExternalServiceException {
		logger.debug("Retrieving AppliedCustomerBillingRate from TMF API with id: {}", acbrId);

        if (acbrId == null) {
            throw new BadTmfDataException("AppliedCustomerBillingRate", acbrId, "AppliedCustomerBillingRate Bill ID cannot be null");
        }

        try {
            AppliedCustomerBillingRate acbr = this.appliedCustomerBillRateApis.getAppliedCustomerBillingRate(acbrId, null);

            if (acbr == null) {
                logger.info("No AppliedCustomerBillingRate found for AppliedCustomerBillingRate with id {}: ", acbrId);
                return null;
            }

            return acbr;
        } catch (Exception e) {
            logger.error("Error retrieving AppliedCustomerBillingRate {}: {}", acbrId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve AppliedCustomerBillingRate with ID: " + acbrId, e);
        }
	}
	
	public boolean existACBRsForCbAndProduct(String customerBillId,String productId)
            throws BadTmfDataException, ExternalServiceException {
	 
        logger.info("Retrieving AppliedCustomerBillingRate from TMF API By Customer Bill with id {} and Product with id {}", customerBillId,productId);

        if (customerBillId == null) {
            throw new BadTmfDataException("CustomerBill", customerBillId, "Customer Bill ID cannot be null");
        }
        
        if (productId == null) {
            throw new BadTmfDataException("Product ", productId, "Product ID cannot be null");
        }

        try {
            Map<String, String> filter = new HashMap<>();
            filter.put("bill.id", customerBillId);
            filter.put("product.id", productId);

            List<AppliedCustomerBillingRate> acbrs = FetchUtils.streamAll(
            		appliedCustomerBillRateApis::listAppliedCustomerBillingRates,    // method reference
                    null,                       		   // fields
                    filter,            					   // filter
                    100                         	       // pageSize
            ).toList();

            if (acbrs == null || acbrs.isEmpty()) {
                logger.info("No AppliedCustomerBillingRate found for Customer Bill with id {} and Product with id {}", customerBillId, productId);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("Error retrieving ACBRs for Customer Bill {} and Product {}: {}", customerBillId,productId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve ACBRs for Customer Bill with ID: " + customerBillId+" and Product with ID: "+productId, e);
        }
    }
	
	/**
     * Fetches appliedCustomerBillingRates in batches from the TMF API based on the provided fields and filter.
     * 
     * @param fields the fields to retrieves
     * @param filter the filter
     * @param batchSize the batch size
     * @param consumer the consumer
     * @throws ExternalServiceException if an error occurs while fetching the AppliedCustomerBillingRate(s)
     */

    public void fetchAppliedCustomerBillRates(String fields, Map<String, String> filter, int batchSize, Consumer<AppliedCustomerBillingRate> consumer) throws ExternalServiceException {
        try {
            FetchUtils.fetchByBatch(
                    (FetchUtils.ListedFetcher<AppliedCustomerBillingRate>) (f, flt, size, offset) ->
                            appliedCustomerBillRateApis.listAppliedCustomerBillingRates(f, flt, size, offset),
                    fields,
                    filter,
                    batchSize,
                    batch -> batch.forEach(consumer)
            );
        } catch (Exception e) {
            logger.error("Failed to fetch AppliedCustomerBillingRates by batch", e);
            throw new ExternalServiceException("Failed to fetch AppliedCustomerBillingRate by batch", e);
        }
    }

}