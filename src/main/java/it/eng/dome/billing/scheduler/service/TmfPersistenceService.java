package it.eng.dome.billing.scheduler.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.billing.scheduler.exception.ExternalServiceException;
import it.eng.dome.billing.scheduler.model.Role;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.model.Invoice;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.BillRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBillCreate;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;
import jakarta.validation.constraints.NotNull;


@Service
public class TmfPersistenceService {

    private final Logger logger = LoggerFactory.getLogger(TmfPersistenceService.class);

    @Autowired 
    private CustomerBillApis customerBillApis;
    
    @Autowired 
    private AppliedCustomerBillRateApis appliedCustomerBillRateApis;
    
    @Autowired
    private TmfDataRetriever tmfDataRetriever;
    
    /**
     * Persists in TMF a list of Invoice related to a product
     * 
     * @param invoices a list of Invoice to persist
     * @param productId the identifier of the Product associated to the invoices
     * @return The list of the persisted Invoice
     * @throws Exception if an error occurs during the processing
     */
    public List<Invoice> persistAllInvoices(@NotNull List<Invoice> invoices, @NotNull String productId) throws Exception{
    	
    	logger.info("Start perstence of {} invoices for Product {}...", invoices.size(),productId);
    	
    	List<Invoice> persistedInvoices=new ArrayList<Invoice>();
    	
    	for(Invoice invoice:invoices) {
    		Invoice persistedInvoice=persistInvoice(invoice, productId);
    		if(persistedInvoice!=null)
    			persistedInvoices.add(persistedInvoice);
    	}
    	
    	logger.info("... {} Invoices persisted for Product {}", persistedInvoices.size(),productId);
    	return persistedInvoices;
    	
    }

    /**
     * Persists in TMF an Invoice related to a product
     * 
     * @param invoice The invoice to persist
     * @param productId the identifier of the Product associated to the invoice
     * @return The persisted Invoice
     * @throws Exception if an error occurs during the processing
     */
    public Invoice persistInvoice(@NotNull Invoice invoice,  @NotNull String productId) throws Exception {
    	
    	CustomerBill persistedCB=this.persistCustomerBill(invoice.getCustomerBill(), productId);
    	
		if(persistedCB!=null) {
			List<AppliedCustomerBillingRate> acbrs=invoice.getAcbrs();
			List<AppliedCustomerBillingRate> persistedAcbrs=new ArrayList<AppliedCustomerBillingRate>();
		
			for (AppliedCustomerBillingRate acbr : acbrs) {
                BillRef billRef = new BillRef();
                billRef.setId(persistedCB.getId());
                acbr.setBill(billRef);
                acbr.setIsBilled(true);
                AppliedCustomerBillingRate persistedAcbr= persistAppliedCustomerBillingRate(acbr);
                persistedAcbrs.add(persistedAcbr);
            }
			
			return new Invoice(persistedCB, persistedAcbrs);
		}
    	else {
    		logger.info("Local Invoice is already on TMF");
            return null;
    	}
    	
	}
    

    /**
     * Persist a CustomerBill if not already present on TMF. 
     * 
     * @param cb the local CustomerBill
     * @param productId the identifier of the Product associated to the bill
     * @return the persisted CustomerBill, or null if already present
     */
    public CustomerBill persistCustomerBill(@NotNull CustomerBill cb, @NotNull String productId) throws Exception {
        CustomerBill existingCustomerBill = isCbAlreadyInTMF(cb, productId);
        
        if (existingCustomerBill == null) {
            CustomerBill cbToPersist = watermark(cb);
            String id = customerBillApis.createCustomerBill(CustomerBillCreate.fromJson(cbToPersist.toJson()));
            logger.info("PERSISTENCE: created CB with id {}", id);
            return tmfDataRetriever.getCustomerBill(id);
        } else {
            logger.info("Local CB is already on TMF with id {}", existingCustomerBill.getId());
            return null;
        }
    }

    /**
     * Persist an AppliedCustomerBillingRate if not already present on TMF. 
     * @param acbr the local AppliedCustomerBillingRate
     * @return The persisted ACBR or the one already present on TMF
     * @throws Exception in case of error
     * 
     */
    public AppliedCustomerBillingRate persistAppliedCustomerBillingRate(@NotNull AppliedCustomerBillingRate acbr) throws Exception {
        AppliedCustomerBillingRate existingACBR = isAcbrAlreadyInTMF(acbr);

        if (existingACBR == null) {
            AppliedCustomerBillingRate acbrToPersist = watermark(acbr);
            AppliedCustomerBillingRateCreate acbrc = AppliedCustomerBillingRateCreate.fromJson(acbrToPersist.toJson());
            String createdId = appliedCustomerBillRateApis.createAppliedCustomerBillingRate(acbrc);
            logger.info("PERSISTENCE: created ACBR with id {}", createdId);
            
            return tmfDataRetriever.getACBR(createdId);
        } else {
            logger.info("Local ACBR is already on TMF with id {}", existingACBR.getId());
            return existingACBR;
        }
    }

    /**
     * Checks if a given CustomerBill already exists in TMF.
     * Uses early-stop fetching: stops as soon as a match is found.
     *
     * @param cb the local CustomerBill to check
     * @param productId the identifier of the Product associated to the CB
     * @return the matched CustomerBill from TMF, or null if none found
     * @throws Exception if any API call fails
     */
    public CustomerBill isCbAlreadyInTMF(@NotNull CustomerBill cb, @NotNull String productId)
            throws Exception {

        // Prepare containers for result & loop control ---
        final CustomerBill[] found = {null};
        final AtomicBoolean stop = new AtomicBoolean(false);

        try {
        	Map<String, String> filter = new HashMap<>();
        	filter.put("billDate",cb.getBillDate().toString());
            filter.put("billingPeriod.startDateTime", cb.getBillingPeriod().getStartDateTime().toString());
            filter.put("billingPeriod.endDateTime",cb.getBillingPeriod().getEndDateTime().toString());
            
            // Iterate all CustomerBills in TMF by batch ---
        	tmfDataRetriever.fetchCustomerBills(null, filter, 50, candidate -> {
                if (stop.get()) return; // short-circuit if already found

                try {
                	
                	// Check Product id
                	boolean productMatch=tmfDataRetriever.existACBRsForCbAndProduct(candidate.getId(), productId);
                	
                    // Compare related parties ---
                    boolean relatedPartyMatchBuyer = relatedPartyMatchBuyer(cb.getRelatedParty(), candidate.getRelatedParty());
                    boolean relatedPartyMatchSeller = relatedPartyMatchSeller(cb.getRelatedParty(), candidate.getRelatedParty());

                    // Compare taxIncludedAmount
                    boolean taxIncludedAmountMatch=this.moneyEquals(cb.getTaxIncludedAmount(), candidate.getTaxIncludedAmount());
                    
                    if (productMatch && relatedPartyMatchBuyer && relatedPartyMatchSeller && taxIncludedAmountMatch) {
                        found[0] = candidate;
                        stop.set(true);
                        logger.debug("Matching CustomerBill found in TMF: {}", candidate.getId());
                    }

                } catch (Exception e) {
                    logger.warn("Error while checking CustomerBill {}: {}", candidate.getId(), e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.error("Error during fetchAllCustomerBills: {}", e.getMessage(), e);
            throw new ExternalServiceException("Failed to search CustomerBill in TMF", e);
        }
        
        return found[0];
    }

    /**
	 * Retrieve ACBRs on TMF that, potentially match the local ACBR. 
	 * @param acbr the local AppliedCustomerBillingRate
	 * @return the matched ACBR, or null if no match.
     * If more than one match is found, an exception is raised.
     *
     * @throws Exception exception in case of error
	 */
    public AppliedCustomerBillingRate isAcbrAlreadyInTMF(@NotNull AppliedCustomerBillingRate acbr) throws Exception {
     	
     	// Prepare containers for result & loop control ---     	
     	final AppliedCustomerBillingRate[] found = {null};
        final AtomicBoolean stop = new AtomicBoolean(false);
         
     	try {
     	
 	        Map<String, String> filter = new HashMap<>();
 	        filter.put("periodCoverage.startDateTime", acbr.getPeriodCoverage().getStartDateTime().toString());
 	        filter.put("periodCoverage.endDateTime",acbr.getPeriodCoverage().getEndDateTime().toString());
 	        filter.put("product.id", acbr.getProduct().getId());
 	        filter.put("billingAccount.id", acbr.getBillingAccount().getId());
         
            // Iterate all CustomerBills in TMF by batch ---
 	        tmfDataRetriever.fetchAppliedCustomerBillRates(null, filter, 50, candidate -> {
 	        	if (stop.get()) return; // short-circuit if already found
 
	            // Compare taxIncludedAmount
	         	boolean taxIncludedAmountMatch=this.moneyEquals(acbr.getTaxIncludedAmount(), candidate.getTaxIncludedAmount());
	         	
	         	//Compare appliedBillingRateType
	         	boolean appliedBillingRateTypeMatch=acbr.getType().equalsIgnoreCase(candidate.getType());
	         
	            if (taxIncludedAmountMatch && appliedBillingRateTypeMatch) {
	                found[0] = candidate;
	                stop.set(true);
	                logger.debug("Matching AppliedCustomerBillingRate found in TMF: {}", candidate.getId());
	            }
 	        });

     	} catch (Exception e) {
     		logger.error("Error during fetchAllCustomerBills: {}", e.getMessage(), e);
     		throw new ExternalServiceException("Failed to search AppliedCustomerBillingRates in TMF", e);
     	}
     
     	return found[0];
        
     }

    
    /**
     *  Compare on the following fields: product, periodcoverage, billingAccount, type, taxExcludedAmount
     *  @param acbr1 first ACBR
     *  @param acbr2 second ACBR
     *  @return true if match, false otherwise
     */
    /*private static boolean match(@NotNull AppliedCustomerBillingRate acbr1, @NotNull AppliedCustomerBillingRate acbr2) {
        Map<String, String> acbr1map = buildComparisonMap(acbr1);
        Map<String, String> acbr2map = buildComparisonMap(acbr2);
        return mapsMatch(acbr1map, acbr2map);
    }*/

    /**
     * Build a map of fields to compare for an ACBR.
     * @param acbr the AppliedCustomerBillingRate
     * @return map of fields to compare
     * 
     */
    /*private static Map<String, String> buildComparisonMap(@NotNull AppliedCustomerBillingRate acbr) {
        Map<String, String> map = new HashMap<>();
        if (acbr.getProduct() != null && acbr.getProduct().getId() != null)
            map.put("product.id", acbr.getProduct().getId());
        if (acbr.getPeriodCoverage() != null) {
            if (acbr.getPeriodCoverage().getStartDateTime() != null)
                map.put("periodCoverage.startDateTime",
                        acbr.getPeriodCoverage().getStartDateTime().truncatedTo(ChronoUnit.SECONDS).toString());
            if (acbr.getPeriodCoverage().getEndDateTime() != null)
                map.put("periodCoverage.endDateTime",
                        acbr.getPeriodCoverage().getEndDateTime().truncatedTo(ChronoUnit.SECONDS).toString());
        }
        if (acbr.getBillingAccount() != null && acbr.getBillingAccount().getId() != null)
            map.put("billingAccount.id", acbr.getBillingAccount().getId());
        if (acbr.getType() != null)
            map.put("type", acbr.getType());
        if (acbr.getTaxExcludedAmount() != null && acbr.getTaxExcludedAmount().getValue() != null)
            map.put("taxExcludedAmount.value", acbr.getTaxExcludedAmount().getValue().toString());
        return map;
    }

    /**
	 * Compare two maps for matching keys and values.
	 * @param map1 first map
	 * @param map2 second map
	 * @return true if maps match, false otherwise
	 */
    
    /*private static boolean mapsMatch(@NotNull Map<String, String> map1, @NotNull Map<String, String> map2) {
        // check all elements in map1 are also in map2 with the same value
        for(String k:map1.keySet()) {
            if(map1.get(k)!=null && !map1.get(k).equals(map2.get(k)))
                return false;
        }
        // check all elements in map2 are also in map1 with the same value
        for(String k:map2.keySet()) {
            if(map2.get(k)!=null && !map2.get(k).equals(map1.get(k)))
                return false;
        }
        return true;
    }*/


    private static AppliedCustomerBillingRate watermark(@NotNull AppliedCustomerBillingRate acbr) {
        // FIXME: marking ACBR for dev, remove before flight

        String mark = "Created by the Billing Scheduler";
        if(acbr.getDescription()!=null) {
            acbr.setDescription(acbr.getDescription() + " - " + mark);
        } else {
            acbr.setDescription(mark);
        }
        return acbr;
    }

   private static CustomerBill watermark(@NotNull CustomerBill cb) {
        // FIXME: marking ACBR for dev, remove before flight

        String mark = "Created by the Billing Scheduler";
        if(cb.getCategory()!=null) {
            cb.setCategory(cb.getCategory() + " - " + mark);
        } else {
            cb.setCategory(mark);
        }
        return cb;
    }

    /**
     * 	Compare related parties of for matching BUYER roles.
     * @param rl1 A first list of relatedParty to match
     * @param rl2 A second list of relatedParty to match
     * @return true is the related parties match BUYER role
     */
    private boolean relatedPartyMatchBuyer(@NotNull List<RelatedParty> rl1,@NotNull List<RelatedParty> rl2) {
    	
        String rlId1 = this.getRelatedPartyIdByRole(rl1, Role.BUYER.getValue());
        if(rlId1==null)
        	rlId1=this.getRelatedPartyIdByRole(rl1, "Customer");
        String rlId2 = this.getRelatedPartyIdByRole(rl2, Role.BUYER.getValue());
        if(rlId2==null)
        	rlId2=this.getRelatedPartyIdByRole(rl2, "Customer");

        return rlId1.equals(rlId2);
    }
    
    /**
     *Compare related parties for matching SELLER roles.
     * @param rl1 A first list of relatedParty to match
     * @param rl2 A second list of relatedParty to match
     * @return true is the related parties match SELLER role
     */
    private boolean relatedPartyMatchSeller(@NotNull List<RelatedParty> rl1, @NotNull List<RelatedParty> rl2) {
        String rlId1 = this.getRelatedPartyIdByRole(rl1, Role.SELLER.getValue());
        String rlId2 = this.getRelatedPartyIdByRole(rl2, Role.SELLER.getValue());

        return rlId1.equals(rlId2);
    }


    /**
     * Gets relatedParty ID by ROLE
     * @param relatedParties A list of RelatedParty
     * @param role the role 
     * @return the related party id for the given role, or null if not found
     */
    private String getRelatedPartyIdByRole(@NotNull List<RelatedParty> relatedParties, String role) {
        if (relatedParties == null || role == null) return null;
        for (RelatedParty rp : relatedParties) {
            if (rp != null && rp.getRole() != null && role.equalsIgnoreCase(rp.getRole()))
                return rp.getId();
        }
        return null;
    }
    
    /*
     * Checks if the two Money TMF678 are equals (same unit and same value)
     * 
     * @param a The first money
     * @param b The second money
     * @return true if the two money are equals
     */
    private boolean moneyEquals(Money a, Money b) {
        if (a == null || b == null) {
            return false;
        }

        // Check unit
        String unitA = a.getUnit();
        String unitB = b.getUnit();
        if (unitA == null || unitB == null) {
            return false;
        }
        if (!unitA.trim().equalsIgnoreCase(unitB.trim())) {
            return false;
        }

        // Check value
        Float valueA = a.getValue();
        Float valueB = b.getValue();
        if (valueA == null || valueB == null) {
            return false;
        }

        // Floating point tolerance
        float EPS = 0.0001f;
        return Math.abs(valueA - valueB) < EPS;
    }

    
}
