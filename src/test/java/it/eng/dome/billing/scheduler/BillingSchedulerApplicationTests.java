package it.eng.dome.billing.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;


@SpringBootTest
class BillingSchedulerApplicationTests {

    @Autowired
    private BillingSchedulerApplication billingSchedulerApplication;

    @Test
    void contextLoads() {
        // to ensure that controller is getting created inside the application context
        assertNotNull(billingSchedulerApplication);
    }

}
