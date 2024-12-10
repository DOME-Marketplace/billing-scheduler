package it.eng.dome.billing.scheduler.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.billing.scheduler.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf637.v4.ApiClient;
import it.eng.dome.tmforum.tmf637.v4.ApiException;
import it.eng.dome.tmforum.tmf637.v4.api.ProductApi;
import it.eng.dome.tmforum.tmf637.v4.model.Product;



@Component(value = "billingService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BillingService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(BillingService.class);
	
	@Autowired
	private TmfApiFactory tmfApiFactory;
	
	private ProductApi productApi;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		final ApiClient apiClient = tmfApiFactory.getTMF637ProductInventoryApiClient();
		productApi = new ProductApi(apiClient);
	}
	
	public String calculateBuilling() throws ApiException {
		
		// 1) retrieve all products
		logger.info("Retrieve all products");
		List<Product> products = productApi.listProduct(null, null, null);
		logger.debug("Size {} ", products.size());
		
		// 2) check if bill is already calculated
		
		// 3) create the bill
		
		// 4) create the invoicing
		
		/*
		  	Verificare quando è il momento di generare il Bill per un Product (check a partire dalla lista dei Product)-> Tipi di Product che si prende in considerazione -> “recurring” (escludiamo il “pay per use”) 

           	Check se il Bill per il Product in questione è già stato calcolato e salvato in tmforum 

			Invocazione del billing in modalità consuntiva (input Product -> output Bill) 
			
			Invocazione dell’invoicing in modalità consuntiva (input Bill -> output Bill) 
			
			Persistenza del Bill su tmforum 
		 */
		
		return null;
	}

}
