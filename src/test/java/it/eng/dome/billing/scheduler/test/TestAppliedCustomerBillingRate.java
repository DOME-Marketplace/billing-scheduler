package it.eng.dome.billing.scheduler.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import it.eng.dome.tmforum.tmf678.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.api.AppliedCustomerBillingRateApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;

public class TestAppliedCustomerBillingRate {

	public static String tmfEndpoint = "https://dome-dev.eng.it";
	

	private static String tmf678CustomerBillingPath = "tmf-api/customerBillManagement/v4";
	
	public static void main(String[] args) {
	
		try {
			
			final it.eng.dome.tmforum.tmf678.v4.ApiClient apiClient = it.eng.dome.tmforum.tmf678.v4.Configuration.getDefaultApiClient();
			apiClient.setBasePath(tmfEndpoint + "/" + tmf678CustomerBillingPath);
			
			AppliedCustomerBillingRateApi applied = new AppliedCustomerBillingRateApi(apiClient);
			List<AppliedCustomerBillingRate> applies = applied.listAppliedCustomerBillingRate(null, null, null, null);
			
			System.out.println("number of appliedCustomerBillingRate found: " + applies.size());
			
			saveApplied(applied);
			
		} catch (ApiException e) {
			System.err.println("Error: " + e.getMessage());
		}

	}
	
	private static void saveApplied(AppliedCustomerBillingRateApi app) {
		System.out.println("saving appliedCustomerBillingRate ...");

		try {
			String json = getJson();
			System.out.println("Payload:\n" + json);
			AppliedCustomerBillingRateCreate acbr = AppliedCustomerBillingRateCreate.fromJson(json);
			app.createAppliedCustomerBillingRate(acbr);
		} catch (ApiException e) {
			System.err.println("Error: " + e.getMessage());
		} catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
		}
	}
	
	private static String getJson() {
		String file = "src/test/resources/appliedcustomerbillingrate.json";
		try {
			return new String(Files.readAllBytes(Paths.get(file)));
		} catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
			return null;
		}
	}

}
