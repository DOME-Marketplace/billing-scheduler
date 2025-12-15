package it.eng.dome.billing.scheduler.exception;

public class BadTmfDataException extends Exception {

	private static final long serialVersionUID = -533369177390155479L;

	public BadTmfDataException(String tmfType, String tmfId, String message, Throwable cause) {
        super(String.format("TMF entity type '%s' with id '%s' has the following issue: %s", tmfType, tmfId, message), cause);
    }

    public BadTmfDataException(String tmfType, String tmfId, String message) {
		super(String.format("TMF entity type '%s' with id '%s' has the following issue: %s", tmfType, tmfId, message));
	}
}
