package it.eng.dome.billing.scheduler.tmf;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;


@Component(value = "tmfApiFactory")
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public final class TmfApiFactory implements InitializingBean {
	
	private static final Logger log = LoggerFactory.getLogger(TmfApiFactory.class);
	
    @Value("${tmforumapi.tmf_endpoint}")
    public String tmfEndpoint;
	
	@Value( "${tmforumapi.tmf637_inventory_path}" )
	private String tmf637ProductInventoryPath;
	
	@Value( "${tmforumapi.tmf678_customer_bill_path}" )
	private String tmf678CustomerBillPath;
	
	public it.eng.dome.tmforum.tmf637.v4.ApiClient getTMF637ProductInventoryApiClient() {
		final it.eng.dome.tmforum.tmf637.v4.ApiClient apiClient = it.eng.dome.tmforum.tmf637.v4.Configuration.getDefaultApiClient();
		apiClient.setBasePath(tmfEndpoint + "/" + tmf637ProductInventoryPath);
		log.debug("Invoke Product Inventory API at endpoint: " + apiClient.getBasePath());
		return apiClient;
	}

	public it.eng.dome.tmforum.tmf678.v4.ApiClient getTMF678ProductInventoryApiClient() {
		final it.eng.dome.tmforum.tmf678.v4.ApiClient apiClient = it.eng.dome.tmforum.tmf678.v4.Configuration.getDefaultApiClient();
		apiClient.setBasePath(tmfEndpoint + "/" + tmf678CustomerBillPath);
		log.debug("Invoke Customer Billing API at endpoint: " + apiClient.getBasePath());
		return apiClient;
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
		
		log.info("Billing Engine is using the following TMForum endpoint prefix: " + tmfEndpoint);	
		
		Assert.state(!StringUtils.isBlank(tmfEndpoint), "Billing Scheduler not properly configured. tmf_endpoint property has no value.");
		Assert.state(!StringUtils.isBlank(tmf637ProductInventoryPath), "Billing Scheduler not properly configured. The tmf637_inventory_path property has no value.");
		Assert.state(!StringUtils.isBlank(tmf678CustomerBillPath), "Billing Scheduler not properly configured. The tmf678_customer_bill_path property has no value.");

		if (tmfEndpoint.endsWith("/")) {
			tmfEndpoint = removeFinalSlash(tmfEndpoint);		
		}
		
		if (tmf637ProductInventoryPath.startsWith("/")) {
			tmf637ProductInventoryPath = removeInitialSlash(tmf637ProductInventoryPath);
		}
		
		if (tmf678CustomerBillPath.startsWith("/")) {
			tmf678CustomerBillPath = removeInitialSlash(tmf678CustomerBillPath);
		}	
			
	}
	
	private String removeFinalSlash(String s) {
		String path = s;
		while (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);

		return path;
	}
	
	private String removeInitialSlash(String s) {
		String path = s;
		while (path.startsWith("/")) {
			path = path.substring(1);
		}				
		return path;
	}	
}