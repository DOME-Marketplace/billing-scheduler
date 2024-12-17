package it.eng.dome.billing.scheduler.test;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import it.eng.dome.tmforum.tmf620.v4.ApiException;
import it.eng.dome.tmforum.tmf620.v4.api.ProductOfferingPriceApi;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
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
	private static String tmf620CatalgPath = "tmf-api/productCatalogManagement/v4";
	
	static OffsetDateTime now = OffsetDateTime.parse("2024-10-12T11:04:38.983Z"); // OffsetDateTime.now();
	
	public static void main(String[] args) {
		try {
			final it.eng.dome.tmforum.tmf637.v4.ApiClient apiClient = it.eng.dome.tmforum.tmf637.v4.Configuration.getDefaultApiClient();
			apiClient.setBasePath(tmfEndpoint + "/" + tmf637ProductInventoryPath);
					
			ProductApi productApi = new ProductApi(apiClient);
			List<Product> products = productApi.listProduct(/*"id,name"*/ null, null, null);
			
			int count = 0;
			for (Product product : products) {
				System.out.println("----------------------------------------------------------");
				System.out.println("item # " + ++count);
				//System.out.println("product id: " + product.getId() + " - " + product.getName() + " -> STATUS: " + product.getStatus());
				
				if (product.getStatus() == ProductStatusType.ACTIVE) {
					// System.out.println("product id active: " + product.getId()); 
					List<ProductPrice> pprices = product.getProductPrice();
					for (ProductPrice pprice : pprices) {
						System.out.println(">> ProductPrice: " + pprice.getName());		
						System.out.println("calcolare se effettuare il bill verificando la scadenza ... ");
						if ("recurring".equals(pprice.getPriceType().toLowerCase()))  {
							if (pprice.getProductOfferingPrice() != null) {
								// check on ProductOfferingPrice 
								// GET recurringChargePeriodType + recurringChargePeriodLength
								System.out.println("ProductOfferingPrice recurring .... " + product.getId());
								System.out.println(pprice.getProductOfferingPrice().getId());
								int period = getPeriod(pprice.getProductOfferingPrice().getId());
								System.out.println("period: " + period);
								if ((period > 0) && isExpiredTime(product.getStartDate(), period)) {
									System.out.println("ProductOfferingPrice product for billing .... " + product.getId());
								}
							}else if (pprice.getRecurringChargePeriod() != null) {// check on RecurringChargePeriod
															
								//get number of days								
								int period = getNumberOfDays(pprice.getRecurringChargePeriod());
								System.out.println(":::: " + period + " -> " + pprice.getRecurringChargePeriod());
								if ((period > 0) && isExpiredTime(product.getStartDate(), period)) {
									System.out.println("RecurringChargePeriod product for billing .... " + product.getId());
									TimePeriod tp = new TimePeriod();
									tp.setStartDateTime(now);
									tp.setEndDateTime(now.plusDays(period));
									System.out.println("AppliedCustomerBillingRate: " + product.getId() + " " + pprice.getName());
									System.out.println(tp);
								}
							}							
						}
					}
				}
			}
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static int getPeriod(String id) {
		final it.eng.dome.tmforum.tmf620.v4.ApiClient apiClient2 = it.eng.dome.tmforum.tmf620.v4.Configuration.getDefaultApiClient();;
		apiClient2.setBasePath(tmfEndpoint + "/" + tmf620CatalgPath);
		ProductOfferingPriceApi poffering = new ProductOfferingPriceApi(apiClient2);
		try {
			ProductOfferingPrice pop = poffering.retrieveProductOfferingPrice(id, null);
			System.out.println("--->>>" + pop.getName() + " >> " + pop.getRecurringChargePeriodType() +" " + pop.getRecurringChargePeriodLength());
			//System.out.println("num days: " + getNumberOfDays(pop.getRecurringChargePeriodType()));
			return pop.getRecurringChargePeriodLength() * getNumberOfDays(pop.getRecurringChargePeriodType());
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return 0;
		}
	}
	
	private static boolean isExpiredTime(OffsetDateTime start, int period) {
		
		System.out.println("check isExpiredTime: " + start + " " + now);
		long days = ChronoUnit.DAYS.between(start, now);
		System.out.println("Difference in days: " + days);
		if (days > 0 && days % period == 0) {
			System.out.println("multiplo");
			return true;
		}else {
			System.out.println("non multiplo");
			return false;
		}
	}
	
	private static int getNumberOfDays(String s) {
		String[] data = s.split("\\s+");
		if (data.length == 2) {
			if (data[0].matches("-?\\d+")) { // if data[0] is a number
				return getDays(Integer.parseInt(data[0]),  data[1]);
			}
		}else if (data.length == 1) {
			return getDays(1, data[0]);
		}		
		return 0;
	}
	
	private static int getDays(int number, String unit) {
		switch (unit) {
			case "day":
	        case "days":
	        case "daily":
	            return number * 1;
	        case "week":
	        case "weeks":
	        case "weekly":
	            return number * 7;
	        case "month":
	        case "months":
	        case "monthly":
	            return number * 30;
	        case "year":
	        case "years":
	            return number * 365; 
	        default:
	            return 0;
	    }
	}

	public static void main1(String[] args) {
				
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