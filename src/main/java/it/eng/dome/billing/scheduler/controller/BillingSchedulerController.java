package it.eng.dome.billing.scheduler.controller;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.billing.scheduler.dto.StartRequestDTO;
import it.eng.dome.billing.scheduler.service.BillingService;
import it.eng.dome.billing.scheduler.validator.TMFEntityValidator;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;

@RestController
@RequestMapping("/billingScheduler")
public class BillingSchedulerController {
	private static final Logger logger = LoggerFactory.getLogger(BillingSchedulerController.class);

	@Autowired
	protected BillingService billingService;
	
	@Value("${billCycle.billCycleSpec_enabled}")
	private boolean billCycleSpecEnabled;
	
	@Autowired
	private TMFEntityValidator tmfEntityValidator;
	
	@PostMapping("/start")
	public ResponseEntity<String> startScheduler(@RequestBody StartRequestDTO datetime) throws Throwable {

		OffsetDateTime now; 
		try {
			String dt = datetime.getDatetime().toString();
			logger.debug("Set datetime manually at {}", dt);
			now = OffsetDateTime.parse(dt);
		} catch (Exception e) {
			String errMsg="Cannot recognize the datetime attribute! Please use the yyyy-MM-dd'T'HH:mm:ss.SSS'Z' format";
			logger.error(errMsg, e.getMessage(), e);
			return new ResponseEntity<String>(errMsg, HttpStatus.BAD_REQUEST);
		}

		logger.info("Start BillingScheduler task via REST APIs to calculate the bill cycle");

		try {
			billingService.calculateBillCycle(now, billCycleSpecEnabled);
			return ResponseEntity.ok("Calculating the bill cycle from datetime: "+now);
			
		}catch(UnsupportedOperationException e) {
			String errMsg="Invocation of not supported method!";
			logger.error(e.getMessage(), e);
			return new ResponseEntity<String>(errMsg, HttpStatus.NOT_IMPLEMENTED);
		}
		
	}
	
	@PostMapping("/validateProductOfferingPrice")
	public ResponseEntity<String> validateProductOfferingPrice(@RequestBody ProductOfferingPrice pop) throws Throwable {

		try {
			tmfEntityValidator.validateProductOfferingPrice(pop);
			String msg=String.format("Successful validation of the POP '%s'", pop.getId());
			logger.info(msg);
			return ResponseEntity.ok(msg);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return new ResponseEntity<String>(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
}
