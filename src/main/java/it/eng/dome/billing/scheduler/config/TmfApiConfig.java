package it.eng.dome.billing.scheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.eng.dome.billing.scheduler.tmf.TmfApiFactory;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;

@Configuration
public class TmfApiConfig {
	
private final Logger logger = LoggerFactory.getLogger(TmfApiConfig.class);
	
	@Autowired
	private TmfApiFactory tmfApiFactory;

	
	@Bean
    public ProductCatalogManagementApis productCatalogManagementApis() {
		logger.info("Initializing of ProductCatalogManagementApis");
		
		return new ProductCatalogManagementApis(tmfApiFactory.getTMF620ProductCatalogApiClient());
	}
	
	@Bean
    public ProductInventoryApis productInventoryApis() {
		logger.info("Initializing of ProductInventoryApis");
		
		return new ProductInventoryApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
	}
	
	@Bean
    public AppliedCustomerBillRateApis appliedCustomerBillRateApis() {
		logger.info("Initializing of AppliedCustomerBillRateApis");
		
		return new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
	}
	
	@Bean
    public CustomerBillApis customerBillApis() {
		logger.info("Initializing of CustomerBillApi");
		
		return new CustomerBillApis(tmfApiFactory.getTMF678CustomerBillApiClient());
	}
}