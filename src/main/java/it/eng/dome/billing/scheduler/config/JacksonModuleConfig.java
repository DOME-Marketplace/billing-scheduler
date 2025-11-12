package it.eng.dome.billing.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.Module;

import it.eng.dome.brokerage.utils.enumappers.TMF620EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF637EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF678EnumModule;

@Configuration
public class JacksonModuleConfig {

	// TMF637EnumModule handles ProductStatusType enum mapping
	@Bean
	public Module getTmf637EnumModule() {
		return new TMF637EnumModule();
	}

	// TMF637EnumModule handles productOffering, productOfferingPrice,
	// productSpecification, catalog, category enum mapping
	@Bean
	public Module getTmf620EnumModule() {
		return new TMF620EnumModule();
	}

	// TMF678EnumModule handles State enum mapping
	@Bean
	public Module getTmf678EnumModule() {
		return new TMF678EnumModule();
	}

}