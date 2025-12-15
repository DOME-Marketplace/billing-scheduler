package it.eng.dome.billing.scheduler.model;

public enum Role {

  BUYER("Buyer"),
  SELLER("Seller"),
  BUYER_OPERATOR("BuyerOperator"),
  SELLER_OPERATOR("SellerOperator"),
  REFERENCE_MARKETPLACE("ReferenceMarketplace");

  private String value;

  private Role(String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }

  @Override
  public String toString() {
    return this.getValue();
  }

  public static Role fromValue(String value) {
    for (Role b : Role.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "' for a Role");
  }

}
