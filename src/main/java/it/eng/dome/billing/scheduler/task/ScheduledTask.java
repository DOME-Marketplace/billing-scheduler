package it.eng.dome.billing.scheduler.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.eng.dome.billing.scheduler.service.BillingSchedulerService;

@Component
@EnableScheduling
public class ScheduledTask {

	private static final Logger logger = LoggerFactory.getLogger(ScheduledTask.class);
	private static final SimpleDateFormat dateformat = new SimpleDateFormat("HH:mm:ss");

	@Autowired
	protected BillingSchedulerService billingService;

	@Scheduled(cron = "${scheduling.cron}")
	public void billingCycleTask() throws Exception {
		logger.info("Scheduling the billing cycle process at {}", dateformat.format(new Date()));

		billingService.manageBillCycle(OffsetDateTime.now(), false);
	}

}
