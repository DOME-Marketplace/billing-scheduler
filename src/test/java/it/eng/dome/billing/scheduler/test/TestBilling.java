package it.eng.dome.billing.scheduler.test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	
	static OffsetDateTime now = OffsetDateTime.parse("2024-12-31T13:14:33.213Z"); // OffsetDateTime.now();
	
	public static void main(String[] args) {
		try {
			final it.eng.dome.tmforum.tmf637.v4.ApiClient apiClient = it.eng.dome.tmforum.tmf637.v4.Configuration.getDefaultApiClient();
			apiClient.setBasePath(tmfEndpoint + "/" + tmf637ProductInventoryPath);
					
			ProductApi productApi = new ProductApi(apiClient);
			List<Product> products = productApi.listProduct(/*"id,name"*/ null, null, null);
			System.out.println("---->>>> Number of Product found = " + products.size());
			
			int count = 0;
			for (Product product : products) {
				System.out.println("----------------------------------------------------------");
				System.out.println("item # " + ++count);
				System.out.println("product - " + product.getName());
				//System.out.println("product id: " + product.getId() + " - " + product.getName() + " -> STATUS: " + product.getStatus());
				
				if (product.getStatus() == ProductStatusType.ACTIVE) {
					// System.out.println("product id active: " + product.getId()); 
					List<ProductPrice> pprices = product.getProductPrice();
					int countPp = 0;
					
					Map<String, List<TimePeriod>> timePeriods = new HashMap<>();
					Map<String, List<ProductPrice>> productPrices = new HashMap<>();
					
					for (ProductPrice pprice : pprices) {
						System.out.println(">> ProductPrice: " + pprice.getName());		
						//System.out.println("checking productPrice ... ");
						if ("recurring".equals(pprice.getPriceType().toLowerCase()))  {
							//System.out.println("priceType = recurring for productPrice");
							System.out.println("productPrice # " + ++countPp);
							
							
							String recurringPeriod = null;
							
							if (pprice.getProductOfferingPrice() != null) {// check on ProductOfferingPrice 								
								// GET recurringChargePeriodType + recurringChargePeriodLength
								System.out.println("Use Case - ProductOfferingPrice for product: " + product.getId());	
								recurringPeriod = getRecurringPeriod( pprice.getProductOfferingPrice().getId());
								System.out.println("Recurring period: " + recurringPeriod);

							}else if (pprice.getRecurringChargePeriod() != null) {// check on RecurringChargePeriod									
								System.out.println("Use Case - RecurringChargePeriod for product: " + product.getId());
								recurringPeriod = pprice.getRecurringChargePeriod();
								System.out.println("Recurring period: " + recurringPeriod);								
							}
							
							if (recurringPeriod != null && product.getStartDate() != null) {
								OffsetDateTime nextBillingTime = getNextBillingTime(product.getStartDate(), recurringPeriod);
								OffsetDateTime previousBillingTime = getPreviousBillingTime(nextBillingTime, recurringPeriod);
								
								if (nextBillingTime != null) {
									System.out.println("StartDate: " + product.getStartDate());
									System.out.println("recurring: " + recurringPeriod);
									System.out.println("NextDate: " + nextBillingTime);
									System.out.println("PreviuosDate: " + previousBillingTime);
									
									
									System.out.println("CurrentDate: " + now);
									long days = ChronoUnit.DAYS.between(now, nextBillingTime);
									System.out.println(">>>days missing for billing: " + days);
									System.out.println("diff: " + ChronoUnit.DAYS.between(previousBillingTime, nextBillingTime));
									String keyPeriod = "period-" + ChronoUnit.DAYS.between(previousBillingTime, nextBillingTime);
									if (days == 0) {
										TimePeriod tp = new TimePeriod();
										tp.setStartDateTime(previousBillingTime);
										tp.setEndDateTime(nextBillingTime);
										System.out.println("AppliedCustomerBillingRate: " + product.getId() + " " + pprice.getName());
										//System.out.println(tp);
										//System.out.println("add: " + keyPeriod + " - obj " + tp);
										
										timePeriods.put(keyPeriod, new ArrayList<>(Arrays.asList(tp)));
										productPrices.computeIfAbsent(keyPeriod, k -> new ArrayList<>()).add(pprice);
									}
								}
							}else {
								System.out.println("No recurringPeriod found or product.startDate valid");
							}
						}
					}
					System.out.println("FINE\n");
					
					//System.out.println(timePeriods);
					//System.out.println(productPrices);
					for (Map.Entry<String, List<ProductPrice>> entry : productPrices.entrySet()) {
						
						String key = entry.getKey();
						System.out.println("key: " + key);
						//
						//System.out.println(pp.getName());
						//List<TimePeriod> tps = timePeriods.get(key);
						TimePeriod tp = timePeriods.get(key).get(0);
						if (timePeriods.get(key).size() > 0) {
							System.out.println("startDate: " + tp.getStartDateTime() + " - endDate: " + tp.getEndDateTime());
							List<ProductPrice> pps = entry.getValue();
							for (ProductPrice pp : pps) {
								System.out.println(pp.getName() + " || " + pp.getPriceType());
							}
							System.out.println("Apply billing - AppliedCustomerBillingRate: " + product.getId());
						}
						
						
					}
				}
			}
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private static String getRecurringPeriod(String id) {
		final it.eng.dome.tmforum.tmf620.v4.ApiClient apiClient2 = it.eng.dome.tmforum.tmf620.v4.Configuration.getDefaultApiClient();;
		apiClient2.setBasePath(tmfEndpoint + "/" + tmf620CatalgPath);
		ProductOfferingPriceApi poffering = new ProductOfferingPriceApi(apiClient2);
		try {
			ProductOfferingPrice pop = poffering.retrieveProductOfferingPrice(id, null);
			//System.out.println("--->>>" + pop.getName() + " >> " + pop.getRecurringChargePeriodType() +" " + pop.getRecurringChargePeriodLength());
			//System.out.println("num days: " + getNumberOfDays(pop.getRecurringChargePeriodType()));
			return pop.getRecurringChargePeriodLength() + " " + pop.getRecurringChargePeriodType();
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	private static OffsetDateTime getNextBillingTime(OffsetDateTime t, String s) {
		String[] data = s.split("\\s+");
		if (data.length == 2) {
			if (data[0].matches("-?\\d+")) { // if data[0] is a number
				return nextBillingTime(t, Integer.parseInt(data[0]),  data[1]);
			}
		}else if (data.length == 1) {
			return nextBillingTime(t, 1, data[0]);
		}		
		return null;
	}

	
	private static OffsetDateTime getPreviousBillingTime(OffsetDateTime t, String s) {
		String[] data = s.split("\\s+");
		if (data.length == 2) {
			if (data[0].matches("-?\\d+")) { // if data[0] is a number
				return getPreviusBilling(t, Integer.parseInt(data[0]),  data[1]);
			}
		}else if (data.length == 1) {
			return getPreviusBilling(t, 1, data[0]);
		}		
		return null;
	}
	
	private static OffsetDateTime getPreviusBilling(OffsetDateTime time, int number, String unit) {
		switch (unit) {
			case "day":
	        case "days":
	        case "daily":
	            return time.minusDays(number);
	        case "week":
	        case "weeks":
	        case "weekly":
	            return time.minusWeeks(number);
	        case "month":
	        case "months":
	        case "monthly":
	            return time.minusMonths(number);
	        case "year":
	        case "years":
	            return time.minusYears(number); 
	        default:
	            return null;
	    }
	}
	
	private static OffsetDateTime nextBillingTime(OffsetDateTime time, int number, String unit) {
		if ((time.toLocalDate().equals(now.toLocalDate()) || (time.isAfter(now)))) {
			return time;
		}else {
		
			switch (unit) {
				case "day":
		        case "days":
		        case "daily":
		            return nextBillingTime(time.plusDays(number), number, unit);
		        case "week":
		        case "weeks":
		        case "weekly":
		            return nextBillingTime(time.plusWeeks(number), number, unit);
		        case "month":
		        case "months":
		        case "monthly":
		            return nextBillingTime(time.plusMonths(number), number, unit);
		        case "year":
		        case "years":
		            return nextBillingTime(time.plusYears(number), number, unit);
		        default:
		            return null;
		    }
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
							System.out.println(bar.getAtBaseType());
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

}