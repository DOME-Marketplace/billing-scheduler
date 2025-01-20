package it.eng.dome.billing.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


@Component(value = "billingFactory")
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class BillingFactory implements InitializingBean {
	
	private static final Logger log = LoggerFactory.getLogger(BillingFactory.class);

	
    @Value("${billing.billing_proxy}")
    public String billinProxy;
    

    @Override
	public void afterPropertiesSet() throws Exception {
		log.info("Billing Proxy is using the following endpoint prefix: " + billinProxy);	
	}

}
