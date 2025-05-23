package it.eng.dome.billing.scheduler.controller;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import it.eng.dome.billing.scheduler.dto.StartRequestDTO;
import it.eng.dome.billing.scheduler.service.BillingService;

@RestController
@RequestMapping("/scheduler")
public class BillingController {
	private static final Logger logger = LoggerFactory.getLogger(BillingController.class);

	@Autowired
	protected BillingService billingService;

	@Operation(responses = { @ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = RESPONSE))) })
	@RequestMapping(value = "/start", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, String> startScheduler(@RequestBody StartRequestDTO datetime) throws Throwable {

		Map<String, String> response = new HashMap<String, String>();
		OffsetDateTime now = OffsetDateTime.now();
		try {
			String dt = datetime.getDatetime().toString();
			logger.debug("Set datetime manually at {}", dt);
			now = OffsetDateTime.parse(dt);
		} catch (Exception e) {
			logger.warn("Cannot recognize the datetime attribute! Please use the YYYY-MM-DDTHH:mm:ss format");
			response.put("msg", "Cannot recognize the datetime attribute! Please use the YYYY-MM-DDTHH:mm:ss format");
			response.put("err", e.getMessage());
		}

		logger.info("Start scheduler task via REST APIs to calculate the bill");

		response.put("response", "Calculating the bill from datetime: " + now);
		billingService.calculateBill(now);
		return response;
	}
	
	private final String RESPONSE = "{\"response\":\"Calculating the bill from datetime: 2025-02-04T16:16:33.171Z\"}";
}
