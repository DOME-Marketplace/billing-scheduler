package it.eng.dome.billing.scheduler.test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import it.eng.dome.tmforum.tmf637.v4.api.ProductApi;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;

import it.eng.dome.tmforum.tmf678.v4.model.AppliedBillingTaxRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillRef;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class TestBilling {
	

    public static String tmfEndpoint = "https://dome-dev.eng.it";
	

	private static String tmf637ProductInventoryPath = "tmf-api/productInventory/v4";

	public static void main(String[] args) {
				
		try {
			final it.eng.dome.tmforum.tmf637.v4.ApiClient apiClient = it.eng.dome.tmforum.tmf637.v4.Configuration.getDefaultApiClient();
			apiClient.setBasePath(tmfEndpoint + "/" + tmf637ProductInventoryPath);
			
			ProductApi productApi = new ProductApi(apiClient);
					
			// 1) retrieve all products
			List<Product> products = productApi.listProduct(/*"id,name"*/ null, null, null);
			System.out.println("number of product found: " + products.size());
			
			// 2) check if bill is already calculated
			
			
			// 3) create the bill
			//TODO Usage of filtering APIs
			int count = 0;
			for (Product product : products) {
				System.out.println("----------------------------------------------------------");
				System.out.println("item # " + ++count);
				System.out.println("product id: " + product.getId() + " - " + product.getName() + " -> STATUS: " + product.getStatus());
				if (product.getStatus() == ProductStatusType.ACTIVE) {
					//System.out.print("status: " +  ProductStatusType.ACTIVE.getValue());
					//System.out.println(" -> retrieved id: " + product.getId());
					//System.out.println(product.getTerminationDate());
					// check if it's necessary to calculate the bill
					//priceType = recurring
					List<ProductPrice> pprices = product.getProductPrice();
					for (ProductPrice pprice : pprices) {
						System.out.println(">> ProductPrice: " + pprice.getName() + " - " + pprice.getPriceType());
						if ("recurring".equals(pprice.getPriceType().toLowerCase())) {
							System.out.println("************ start bill for " + product.getId());
							
							AppliedBillingTaxRate abtr = new AppliedBillingTaxRate();
							abtr.setTaxCategory("VAT");
							abtr.setTaxRate((float) 23);
							abtr.setTaxAmount(new Money().unit("EUR").value((float)10));
													
							BillRef bill = new BillRef().id("Bi-123").href("Bi-123");
							
							BillingAccountRef bar = new BillingAccountRef().id("bar-123423").name("billing accoun test");
							
							ProductRef pr = new ProductRef().id("my-prod-id1").name("CSPs wonderful fiber service");
							
							AppliedCustomerBillingRate acbr = new AppliedCustomerBillingRate();
							acbr.setDescription("Billing for test");
							acbr.addAppliedTaxItem(abtr);
							acbr.setBill(bill);
							acbr.isBilled(true);
							//acbr.setBillingAccount(bar);							
							acbr.setProduct(pr);
							acbr.setTaxIncludedAmount(new Money().unit("EUR").value((float)21));
							acbr.setType("**********");
							acbr.setPeriodCoverage(new TimePeriod().startDateTime(OffsetDateTime.now(ZoneOffset.UTC)).endDateTime(OffsetDateTime.now(ZoneOffset.UTC).plusYears(1)));
							acbr.setAtSchemaLocation(new URI(tmfEndpoint));
							// If an AppliedCustomerBillingRate is billed, the bill needs to be included.
							// If an AppliedCustomerBillingRate is not yet billed, the billing account needs to be included.
							
							System.out.println("---------- JSON ------------");
							System.out.println(acbr.toJson());
							System.out.println("---------- JSON ------------");
							
							
							//break;
						}else {
							System.out.println("no bill for priceType <> recurring");
						}
						
					}
				}else {
					System.out.println("scartato " + product.getId() + " - " + product.getName());
				}
				
			}
			
			// 4) create the invoicing
		
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void bill() {
		System.out.println("calculate the bill");
	}

}