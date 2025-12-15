package it.eng.dome.billing.scheduler.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.brokerage.observability.AbstractHealthService;
import it.eng.dome.brokerage.observability.health.Check;
import it.eng.dome.brokerage.observability.health.Health;
import it.eng.dome.brokerage.observability.health.HealthStatus;
import it.eng.dome.brokerage.observability.info.Info;


@Service
public class HealthService extends AbstractHealthService {
	
	private final Logger logger = LoggerFactory.getLogger(HealthService.class);
	private final static String SERVICE_NAME = "Billing Scheduler";
		
	
	@Autowired
    private BillingFactory billingFactory;
	
	private final ProductCatalogManagementApis productCatalogManagementApis;
	private final ProductInventoryApis productInventoryApis;
	private final AppliedCustomerBillRateApis appliedCustomerBillRateApis;
	
	public HealthService(ProductCatalogManagementApis productCatalogManagementApis, 
			ProductInventoryApis productInventoryApis, AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
		
		this.productCatalogManagementApis = productCatalogManagementApis;
		this.productInventoryApis = productInventoryApis;
		this.appliedCustomerBillRateApis = appliedCustomerBillRateApis;
	}
	
	@Override
	public Info getInfo() {

		Info info = super.getInfo();
		logger.debug("Response: {}", toJson(info));

		return info;
	}
	
	@Override
	public Health getHealth() {
		Health health = new Health();
		health.setDescription("Health for the " + SERVICE_NAME);

		health.elevateStatus(HealthStatus.PASS);
		
		// 1: check of Bills Service dependencies
		for (Check c : getBillsServiceCheck()) {
			health.addCheck(c);
			health.elevateStatus(c.getStatus());
		}

		// 2: check dependencies: in case of FAIL or WARN set it to WARN
		boolean onlyDependenciesFailing = health.getChecks("self", null).stream()
				.allMatch(c -> c.getStatus() == HealthStatus.PASS);
		
		if (onlyDependenciesFailing && health.getStatus() == HealthStatus.FAIL) {
	        health.setStatus(HealthStatus.WARN);
	    }

		// 3: check self info
		Check selfInfo = getChecksOnSelf(SERVICE_NAME);
		health.addCheck(selfInfo);
		health.elevateStatus(selfInfo.getStatus());
	    
	    // 4: check of the TMForum APIs dependencies
 		for (Check c : getTMFChecks()) {
 			health.addCheck(c);
 			health.elevateStatus(c.getStatus());
 		}
	    
	    // 5: build human-readable notes
	    health.setNotes(buildNotes(health));
		
		logger.debug("Health response: {}", toJson(health));

		return health;
	}
	
	private List<Check> getTMFChecks() {

		List<Check> out = new ArrayList<>();

		// TMF620
		Check tmf620 = createCheck("tmf-api", "connectivity", "tmf620");

		try {
			FetchUtils.streamAll(productCatalogManagementApis::listProductOfferingPrices, null, null, 1).findAny();

			tmf620.setStatus(HealthStatus.PASS);

		} catch (Exception e) {
			tmf620.setStatus(HealthStatus.FAIL);
			tmf620.setOutput(e.toString());
		}

		out.add(tmf620);
	
		// TMF637
		Check tmf637 = createCheck("tmf-api", "connectivity", "tmf637");

		try {
			FetchUtils.streamAll(productInventoryApis::listProducts, null, null, 1).findAny();

			tmf637.setStatus(HealthStatus.PASS);

		} catch (Exception e) {
			tmf637.setStatus(HealthStatus.FAIL);
			tmf637.setOutput(e.toString());
		}

		out.add(tmf637);
		
		// TMF678
		Check tmf678 = createCheck("tmf-api", "connectivity", "tmf678");

		try {
			FetchUtils.streamAll(
				appliedCustomerBillRateApis::listAppliedCustomerBillingRates,
			    null,
			    null,
			    1
			)
			.findAny();
			
			tmf678.setStatus(HealthStatus.PASS);
		} catch (Exception e) {
			tmf678.setStatus(HealthStatus.FAIL);
			tmf678.setOutput(e.toString());
		}

		out.add(tmf678);

		return out;
	}
	
	private List<Check> getBillsServiceCheck() {

		List<Check> out = new ArrayList<>();

        Check invoicing = createCheck("billing-proxy", "connectivity", "external");

        try {
        	Info invoicingInfo = billingFactory.getInfoBillingProxy();
        	invoicing.setStatus(HealthStatus.PASS);
        	invoicing.setOutput(toJson(invoicingInfo));
        }
        catch(Exception e) {
        	invoicing.setStatus(HealthStatus.FAIL);
        	invoicing.setOutput(e.getMessage());
        }
        out.add(invoicing);
 
		return out;
	}

}