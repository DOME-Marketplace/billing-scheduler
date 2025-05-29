package it.eng.dome.billing.scheduler.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import it.eng.dome.tmforum.tmf678.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.api.CustomerBillApi;
import it.eng.dome.tmforum.tmf678.v4.api.CustomerBillExtensionApi;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBillCreate;

public class TestCustomerBill {

	public static String tmfEndpoint = "https://dome-dev.eng.it";
	

	private static String tmf678CustomerBillingPath = "tmf-api/customerBillManagement/v4";
	
	public static void main(String[] args) {
	
		try {
			
			final it.eng.dome.tmforum.tmf678.v4.ApiClient apiClient = it.eng.dome.tmforum.tmf678.v4.Configuration.getDefaultApiClient();
			apiClient.setBasePath(tmfEndpoint + "/" + tmf678CustomerBillingPath);
			
			CustomerBillApi customer = new CustomerBillApi(apiClient);
			List<CustomerBill> customers = customer.listCustomerBill(null, null, null, null);
			
			System.out.println("number of customerBill found: " + customers.size());
			
			CustomerBillExtensionApi customerCreate = new CustomerBillExtensionApi(apiClient);
			
			saveCustomerBilling(customerCreate);
			
			
			customers = customer.listCustomerBill(null, null, null, null);
			System.out.println("number of customerBill found: " + customers.size());
			for (CustomerBill customerBill : customers) {
				System.out.println("customerId: " + customerBill.getId());
			}
			
		} catch (ApiException e) {
			System.err.println("Error: " + e.getMessage());
		}

	}
	
	protected static void saveCustomerBilling(CustomerBillExtensionApi create) {
		System.out.println("saving customerBill ...");
		
		try {
			String json = getJson();
			System.out.println("Payload:\n" + json);
			CustomerBillCreate cbc = CustomerBillCreate.fromJson(json);
			
			create.createCustomerBill(cbc);
		} catch (ApiException | IOException e) {
			System.err.println("Error: " + e.getMessage());
		}
	}
	
	private static String getJson() {
		String file = "src/test/resources/customerbill.json";
		try {
			return new String(Files.readAllBytes(Paths.get(file)));
		} catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
			return null;
		}
	}

}
