package it.eng.dome.billing.scheduler.exception;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.dome.brokerage.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(ControllerExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	protected ResponseEntity<Object> handleIllegalArgumentException(HttpServletRequest request, IllegalArgumentException ex) {
		return buildResponseEntity(new ErrorResponse(request, HttpStatus.BAD_REQUEST, ex));
	}
	
	@ExceptionHandler(IllegalStateException.class)
	protected ResponseEntity<Object> handleIllegalStateException(HttpServletRequest request, IllegalStateException ex) {
		return buildResponseEntity(new ErrorResponse(request,HttpStatus.BAD_REQUEST, ex));
	}
		
	@ExceptionHandler(ConnectException.class)
	protected ResponseEntity<Object> handleConnectionException(HttpServletRequest request, ConnectException ex) {
		return buildResponseEntity(new ErrorResponse(request,HttpStatus.SERVICE_UNAVAILABLE, ex));
	}	
	
	@ExceptionHandler(UnknownHostException.class)
	protected ResponseEntity<Object> handleConnectionException(HttpServletRequest request, UnknownHostException ex) {
		return buildResponseEntity(new ErrorResponse(request,HttpStatus.SERVICE_UNAVAILABLE, ex));
	}
		
	@ExceptionHandler(HttpClientErrorException.class)
	protected ResponseEntity<Object> handleBadRequest(HttpServletRequest request, HttpClientErrorException ex) {
		return buildResponseEntity(new ErrorResponse(request, HttpStatus.BAD_REQUEST, ex));
	}

	@ExceptionHandler(HttpServerErrorException.class)
	protected ResponseEntity<Object> handleInternalServerError(HttpServletRequest request, HttpServerErrorException ex) {
		return buildResponseEntity(new ErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, ex));
	}

	@ExceptionHandler(SocketTimeoutException.class)
	protected ResponseEntity<Object> handleSocketTimeoutError(HttpServletRequest request, SocketTimeoutException ex) {
		return buildResponseEntity(new ErrorResponse(request, HttpStatus.REQUEST_TIMEOUT, ex));
	}
	
	@ExceptionHandler(it.eng.dome.tmforum.tmf637.v4.ApiException.class)
	protected ResponseEntity<Object> handleApiException(HttpServletRequest request, it.eng.dome.tmforum.tmf637.v4.ApiException ex) {
		return buildResponseEntity(new ErrorResponse(request, HttpStatus.SERVICE_UNAVAILABLE, ex));
	}
	
	@ExceptionHandler(it.eng.dome.tmforum.tmf620.v4.ApiException.class)
	protected ResponseEntity<Object> handleApiException(HttpServletRequest request, it.eng.dome.tmforum.tmf620.v4.ApiException ex) {
		return buildResponseEntity(new ErrorResponse(request, HttpStatus.SERVICE_UNAVAILABLE, ex));
	}
	
	@ExceptionHandler(it.eng.dome.tmforum.tmf678.v4.ApiException.class)
	protected ResponseEntity<Object> handleApiException(HttpServletRequest request, it.eng.dome.tmforum.tmf678.v4.ApiException ex) {
		return buildResponseEntity(new ErrorResponse(request, HttpStatus.SERVICE_UNAVAILABLE, ex));
	}	
	
	private ResponseEntity<Object> buildResponseEntity(ErrorResponse errorResponse) {
		logger.error("{} - {}", errorResponse.getStatus(), errorResponse.getMessage());
		return new ResponseEntity<>(errorResponse, errorResponse.getStatus());
	}
}