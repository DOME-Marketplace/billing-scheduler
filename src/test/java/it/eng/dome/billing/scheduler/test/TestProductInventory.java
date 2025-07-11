package it.eng.dome.billing.scheduler.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.tmforum.tmf637.v4.ApiClient;
import it.eng.dome.tmforum.tmf637.v4.Configuration;
import it.eng.dome.tmforum.tmf637.v4.api.ProductApi;
import it.eng.dome.tmforum.tmf637.v4.model.Product;

public class TestProductInventory {

	public static String tmfEndpoint = "https://dome-dev.eng.it";
	
	private static String tmf637ProductInventoryPath = "tmf-api/productInventory/v4";
	
	public static void main(String[] args) {
		TestProductInventory test = new TestProductInventory();
		//test.test1();
		//test.test2();
		
		test.TestKey();
	}
	
	public void TestKey() {
		System.out.println("my test");
		String input = "recurring Postpaid";
		String normalized = BillingPriceType.normalize(input);
		System.out.println("--> " + normalized);
		System.out.println( Arrays.stream(BillingPriceType.values())
	      .map(BillingPriceType::getNormalizedKey)
	      .distinct()
	      .collect(Collectors.joining(", ")));
		
	}
	
	public enum BillingPriceType {
	    RECURRING("recurring"),
	    RECURRING_PREPAID("recurring-prepaid"),
	    RECURRING_POSTPAID("recurring-postpaid"),
	    PAY_PER_USE("pay-per-use");

	    private final String normalizedKey;

	    BillingPriceType(String normalizedKey) {
	        this.normalizedKey = normalizedKey;
	    }

	    public String getNormalizedKey() {
	        return normalizedKey;
	    }

	    public static String normalize(String input) {
	    	if (input == null) return null;
	        String normalizedInput = input
	            //.toLowerCase()
	            .trim()
	            .replaceAll("\\s+", "-")
	            //.toUpperCase()
	            .replace("-", "_");
	        System.out.println(normalizedInput);
	        for (BillingPriceType type : values()) {
	            if (type.name().equalsIgnoreCase(normalizedInput)) {
	                return type.getNormalizedKey();
	            }
	        }
	        return null;
	    }
	}
	
	public void test1() {
		try {
			final ApiClient apiClient = Configuration.getDefaultApiClient();
			apiClient.setBasePath(tmfEndpoint + "/" + tmf637ProductInventoryPath);
			
			System.out.println("Endppoint: " + apiClient.getBasePath());

			ProductApi productApi = new ProductApi(apiClient);
			List<Product> products = productApi.listProduct(/* "id,name" */ null, null, null, null);
			//System.out.println(products.get(0).toJson());
			System.out.println("---->>>> Number of Product found = " + products.size());
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
	}
	
	public void test2() {
		try {
			String json = getJsonProducts();
			System.out.println("Payload:\n" + json);
			
		 
	        ObjectMapper objectMapper = new ObjectMapper();
	        objectMapper.registerModule(new JavaTimeModule());
	        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	        List<Product> products = objectMapper.readValue(json, new TypeReference<List<Product>>() {});
	        System.out.println(products.size());

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static String getJsonProducts() {
		String file = "src/test/resources/products.json";
		try {
			return new String(Files.readAllBytes(Paths.get(file)));
		} catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
			return null;
		}
	}
}
