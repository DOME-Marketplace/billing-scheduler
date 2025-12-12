package it.eng.dome.billing.scheduler.persistence;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.billing.scheduler.exception.BadTmfDataException;
import it.eng.dome.billing.scheduler.exception.ExternalServiceException;
import it.eng.dome.billing.scheduler.model.Role;
import it.eng.dome.billing.scheduler.service.TmfDataRetriever;
import it.eng.dome.billing.scheduler.service.TmfPersistenceService;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;
import it.eng.dome.tmforum.tmf678.v4.ApiClient;
import it.eng.dome.tmforum.tmf678.v4.Configuration;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.validation.constraints.NotNull;

public class TestPersistenceInvoices {
	
	final static String tmf678CustomerBillPath = "tmf-api/customerBillManagement/v4";
	final static String tmfEndpoint = "https://dome-dev.eng.it"; // "https://tmf.dome-marketplace-sbx.org";
	
	private final Logger logger = LoggerFactory.getLogger(TestPersistenceInvoices.class);
	
	public static void main(String[] args) {
		
		TestPersistenceInvoices test=new TestPersistenceInvoices();
		
		CustomerBill cb=new CustomerBill();
		cb.setBillDate(OffsetDateTime.parse("2025-10-13T10:04:38.983Z"));
		TimePeriod tp=new TimePeriod();
		tp.setStartDateTime(OffsetDateTime.parse("2025-10-04T10:04:38.983Z"));
		tp.setEndDateTime(OffsetDateTime.parse("2025-10-13T10:04:38.983Z"));
		cb.setBillingPeriod(tp);
		Money money=new Money();
		money.setUnit("EUR");
		money.setValue(5.0f);
		
		Money money2=new Money();
		money2.setUnit("EUR");
		money2.setValue(5f);
		
		boolean moneyEq= money.equals(money2);
		System.out.println("MoneyEq: "+moneyEq);
		
		List<RelatedParty> relatedParties=new ArrayList<RelatedParty>();
		RelatedParty rp1=new RelatedParty();
		RelatedParty rp2=new RelatedParty();
		rp1.setRole(Role.BUYER.getValue());
		rp1.setId("urn:ngsi-ld:organization:38817de3-8c3e-4141-a344-86ffd915cc3b");
		
		rp2.setRole(Role.SELLER.getValue());
		rp2.setId("urn:ngsi-ld:organization:38063c78-fc9f-42ca-a39e-518107a2d403");
		
		relatedParties.add(rp1);
		relatedParties.add(rp2);
		
		cb.setTaxIncludedAmount(money);
		cb.setRelatedParty(relatedParties);
		
		AppliedCustomerBillingRate acbr=new AppliedCustomerBillingRate();
		acbr.setTaxIncludedAmount(money);
		acbr.setPeriodCoverage(tp);
		BillingAccountRef accountRef=new BillingAccountRef();
		accountRef.setId("urn:ngsi-ld:billing-account:3bf025cb-1b58-48be-b0ae-bb0967d09d3b");
		acbr.setBillingAccount(accountRef);
		
		ProductRef prodRef=new ProductRef();
		prodRef.setId("urn:ngsi-ld:product:33a92eeb-2b8a-4c78-81c4-97a64a7b4fac");
		acbr.setProduct(prodRef);
		acbr.setType("recurring");
		
		try {
			CustomerBill cbTMF=test.isCbAlreadyInTMF(cb, "urn:ngsi-ld:product:33a92eeb-2b8a-4c78-81c4-97a64a7b4fac");
			if(cbTMF==null) {
				System.out.println("CB No in TMF");
			}else {
				System.out.println("CB in TMF");
			}
			
			AppliedCustomerBillingRate acbrTMF=test.isAcbrAlreadyInTMF(acbr);
			if(acbrTMF==null) {
				System.out.println("ACBR No in TMF");
			}else {
				System.out.println("ACBR in TMF");
			}
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
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
	        	this.fetchCustomerBills(null, filter, 50, candidate -> {
	                if (stop.get()) return; // short-circuit if already found

	                try {
	                    // --- Step 4: Compare billing periods (truncate to seconds for precision consistency) ---
						/*
						 * OffsetDateTime cbStart = cb.getBillingPeriod() != null ?
						 * cb.getBillingPeriod().getStartDateTime().truncatedTo(ChronoUnit.SECONDS) :
						 * null; OffsetDateTime cbEnd = cb.getBillingPeriod() != null ?
						 * cb.getBillingPeriod().getEndDateTime().truncatedTo(ChronoUnit.SECONDS) :
						 * null; OffsetDateTime candStart = candidate.getBillingPeriod() != null ?
						 * candidate.getBillingPeriod().getStartDateTime().truncatedTo(ChronoUnit.
						 * SECONDS) : null; OffsetDateTime candEnd = candidate.getBillingPeriod() !=
						 * null ?
						 * candidate.getBillingPeriod().getEndDateTime().truncatedTo(ChronoUnit.SECONDS)
						 * : null;
						 * 
						 * boolean periodMatch = Objects.equals(cbStart, candStart) &&
						 * Objects.equals(cbEnd, candEnd);
						 */

	                    // Compare Product IDs via first AppliedCustomerBillingRate ---
						/*
						 * List<AppliedCustomerBillingRate> candAcbrs =
						 * this.getACBRsByCustomerBillId(candidate.getId()); String candProductId =
						 * candAcbrs.isEmpty() || candAcbrs.get(0).getProduct() == null ? null :
						 * candAcbrs.get(0).getProduct().getId();
						 */
	                	
	                	boolean productMatch=this.existACBRsForCbAndProduct(candidate.getId(), productId);
	                	
	                	System.out.println("productMatch: "+productMatch);
	                	
	                    // Compare related parties ---
	                    boolean relatedPartyMatchBuyer = relatedPartyMatchBuyer(cb.getRelatedParty(), candidate.getRelatedParty());
	                    boolean relatedPartyMatchSeller = relatedPartyMatchSeller(cb.getRelatedParty(), candidate.getRelatedParty());
	                    
	                    System.out.println("relatedPartyMatchBuyer: "+relatedPartyMatchBuyer);
	                    System.out.println("relatedPartyMatchSeller: "+relatedPartyMatchSeller);
	                    
	                    // Compare billDate
	                    //boolean billDate=candidate.getBillDate().isEqual(cb.getBillDate());
	                    
	                    // Compare amountDue
	                    boolean taxIncludedAmountMatch=this.moneyEquals(cb.getTaxIncludedAmount(), candidate.getTaxIncludedAmount());
	                    System.out.println("taxIncludedAmountMatch: "+taxIncludedAmountMatch);
	                    
	                    

	                    // If all conditions match, store & stop ---
	                    /*if (periodMatch && Objects.equals(productId, candProductId) && relatedPartyMatchBuyer && relatedPartyMatchSeller && billDate && amountDue) {
	                        found[0] = candidate;
	                        stop.set(true);
	                        logger.debug("Matching CustomerBill found in TMF: {}", candidate.getId());
	                    }*/
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

	        // Return the found match (or null if none) ---
	        return found[0];
	    }
	 
	 public void fetchCustomerBills(String fields, Map<String, String> filter, int batchSize, Consumer<CustomerBill> consumer) throws ExternalServiceException {
	     
		 ApiClient apiClientTmf678 = Configuration.getDefaultApiClient();
		 apiClientTmf678.setBasePath(tmfEndpoint + "/" + tmf678CustomerBillPath);
			
		 CustomerBillApis cbApis=new CustomerBillApis(apiClientTmf678);
			
		 try {
			 AtomicInteger counter = new AtomicInteger();
			 
	            FetchUtils.fetchByBatch(
	                    (FetchUtils.ListedFetcher<CustomerBill>) (f, flt, size, offset) ->
	                    cbApis.listCustomerBills(f, flt, size, offset),
	                    fields,
	                    filter,
	                    batchSize,
	                    batch -> {
	                    	counter.addAndGet(batch.size());
	                    	batch.forEach(consumer);
	                    }
	            );
	            
	            System.out.println("Totale CustomerBill recuperati: " + counter.get());
	        } catch (Exception e) {
	            logger.error("Failed to fetch CustomerBills by batch", e);
	            throw new ExternalServiceException("Failed to fetch CustomerBills by batch", e);
	        }
	    }
	 
	 public List<AppliedCustomerBillingRate> getACBRsByCustomerBillId(String customerBillId)
	            throws BadTmfDataException, ExternalServiceException {
		 
			ApiClient apiClientTmf678 = Configuration.getDefaultApiClient();
			apiClientTmf678.setBasePath(tmfEndpoint + "/" + tmf678CustomerBillPath);
				
			AppliedCustomerBillRateApis acbrsApis=new AppliedCustomerBillRateApis(apiClientTmf678);
		 
	        logger.info("Retrieving AppliedCustomerBillingRate from TMF API By Customer Bill with id: {}", customerBillId);

	        if (customerBillId == null) {
	            throw new BadTmfDataException("CustomerBill", customerBillId, "Customer Bill ID cannot be null");
	        }

	        try {
	            Map<String, String> filter = new HashMap<>();
	            filter.put("bill.id", customerBillId);
	            //FIXME: fix retrieve of large ACBR lists
	            List<AppliedCustomerBillingRate> acbrs = FetchUtils.streamAll(
	            		acbrsApis::listAppliedCustomerBillingRates,    // method reference
	                    null,                       		   // fields
	                    filter,            					   // filter
	                    100                         	       // pageSize
	            ).toList();

	            if (acbrs == null || acbrs.isEmpty()) {
	                logger.info("No AppliedCustomerBillingRate found for Customer Bill with id {}.", customerBillId);
	                return Collections.emptyList();
	            }

	            return acbrs;
	        } catch (Exception e) {
	            logger.error("Error retrieving ACBRs for Customer Bill {}: {}", customerBillId, e.getMessage(), e);
	            throw new ExternalServiceException("Failed to retrieve ACBRs for Customer Bill with ID: " + customerBillId, e);
	        }
	    }
	 
	 public boolean existACBRsForCbAndProduct(String customerBillId,String productId)
	            throws BadTmfDataException, ExternalServiceException {
		 
			ApiClient apiClientTmf678 = Configuration.getDefaultApiClient();
			apiClientTmf678.setBasePath(tmfEndpoint + "/" + tmf678CustomerBillPath);
				
			AppliedCustomerBillRateApis acbrsApis=new AppliedCustomerBillRateApis(apiClientTmf678);
		 
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
	            //FIXME: fix retrieve of large ACBR lists
	            List<AppliedCustomerBillingRate> acbrs = FetchUtils.streamAll(
	            		acbrsApis::listAppliedCustomerBillingRates,    // method reference
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
	 	        //filter.put("type", acbr.getType());
	         
	         // Iterate all CustomerBills in TMF by batch ---
	 	        this.fetchAppliedCustomerBillRates(null, filter, 50, candidate -> {
	 	        	if (stop.get()) return; // short-circuit if already found
	        
	            // try {

	                 // Compare taxIncludedAmount
	                 	boolean taxIncludedAmountMatch=this.moneyEquals(acbr.getTaxIncludedAmount(), candidate.getTaxIncludedAmount());
	                 	
	                 	//Compare appliedBillingRateType
	                 	boolean appliedBillingRateTypeMatch=acbr.getType().equalsIgnoreCase(candidate.getType());
	                 
	 	                if (taxIncludedAmountMatch && appliedBillingRateTypeMatch) {
	 	                    found[0] = candidate;
	 	                    stop.set(true);
	 	                    logger.debug("Matching AppliedCustomerBillingRate found in TMF: {}", candidate.getId());
	 	                }

	            // } catch (Exception e) {
	                 //logger.warn("Error while checking CustomerBill {}: {}", candidate.getId(), e.getMessage());
	            // }
	 	        });

	     	} catch (Exception e) {
	     		logger.error("Error during fetchAllCustomerBills: {}", e.getMessage(), e);
	     		throw new ExternalServiceException("Failed to search AppliedCustomerBillingRates in TMF", e);
	     	}
	     
	     	return found[0];
	        
	     }
	    
	    public void fetchAppliedCustomerBillRates(String fields, Map<String, String> filter, int batchSize, Consumer<AppliedCustomerBillingRate> consumer) throws ExternalServiceException {
	        
	    	ApiClient apiClientTmf678 = Configuration.getDefaultApiClient();
			apiClientTmf678.setBasePath(tmfEndpoint + "/" + tmf678CustomerBillPath);
				
			AppliedCustomerBillRateApis acbrsApis=new AppliedCustomerBillRateApis(apiClientTmf678);
			
			AtomicInteger counter = new AtomicInteger();
			
	    	try {
	            FetchUtils.fetchByBatch(
	                    (FetchUtils.ListedFetcher<AppliedCustomerBillingRate>) (f, flt, size, offset) ->
	                    	acbrsApis.listAppliedCustomerBillingRates(f, flt, size, offset),
	                    fields,
	                    filter,
	                    batchSize,
	                    batch -> {
	                    	counter.addAndGet(batch.size());
	                    	batch.forEach(consumer);
	                    }
	            );
	            System.out.println("Totale ACBRs recuperati: " + counter.get());
	        } catch (Exception e) {
	            logger.error("Failed to fetch AppliedCustomerBillingRates by batch", e);
	            throw new ExternalServiceException("Failed to fetch AppliedCustomerBillingRate by batch", e);
	        }
	    }
}
