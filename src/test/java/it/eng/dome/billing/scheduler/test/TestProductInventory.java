package it.eng.dome.billing.scheduler.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
		test.test2();
	}
	
	public void test1() {
		try {
			final ApiClient apiClient = Configuration.getDefaultApiClient();
			apiClient.setBasePath(tmfEndpoint + "/" + tmf637ProductInventoryPath);
			
			System.out.println("Endppoint: " + apiClient.getBasePath());

			ProductApi productApi = new ProductApi(apiClient);
			List<Product> products = productApi.listProduct(/* "id,name" */ null, null, null);
			//System.out.println(products.get(0).toJson());
			System.out.println("---->>>> Number of Product found = " + products.size());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void test2() {
		try {
			String json = getJsonProducts();
			System.out.println("Payload:\n" + json);
			
		 
	        ObjectMapper objectMapper = new ObjectMapper();
	        List<Product> products = objectMapper.readValue(json, new TypeReference<List<Product>>() {});
	        //products.forEach(System.out::println);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static String getJsonProducts() {
		String file = "src/test/resources/productsAccessNode.json";
		try {
			return new String(Files.readAllBytes(Paths.get(file)));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
}
