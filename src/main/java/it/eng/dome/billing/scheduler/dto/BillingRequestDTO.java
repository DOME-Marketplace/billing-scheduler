package it.eng.dome.billing.scheduler.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class BillingRequestDTO {
	
	private Product product;
	private TimePeriod timePeriod;
	private List<ProductPrice> productPrice;
	
	public BillingRequestDTO(){}
	
	@JsonCreator
	public BillingRequestDTO(@JsonProperty("product") Product pr, @JsonProperty("timePeriod") TimePeriod tp, @JsonProperty("productPrice") List<ProductPrice> ppl) {
		this.setProduct(pr);
		this.setTimePeriod(tp);
		this.setProductPrice(ppl);
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public TimePeriod getTimePeriod() {
		return timePeriod;
	}

	public void setTimePeriod(TimePeriod timePeriod) {
		this.timePeriod = timePeriod;
	}

	public List<ProductPrice> getProductPrice() {
		return productPrice;
	}

	public void setProductPrice(List<ProductPrice> productPrice) {
		this.productPrice = productPrice;
	}
}