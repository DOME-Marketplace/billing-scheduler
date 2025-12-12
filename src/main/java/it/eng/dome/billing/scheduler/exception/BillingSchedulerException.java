package it.eng.dome.billing.scheduler.exception;

/**
 * General BillingScheduler exception
 */
public class BillingSchedulerException extends Exception {

	private static final long serialVersionUID = -6798007748994356476L;

	private String message;

	public BillingSchedulerException(String message) {
		super();
		this.message = message;
	}

	@Override
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

}
