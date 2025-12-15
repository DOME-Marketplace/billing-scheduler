package it.eng.dome.billing.scheduler.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TrailingSlashFilter extends OncePerRequestFilter {

	private final Logger logger = LoggerFactory.getLogger(TrailingSlashFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String requestURI = request.getRequestURI();

		if (requestURI.length() > 1 && requestURI.endsWith("/")) {
			logger.debug("Apply filter to remove trailing slash from [{} {}]", request.getMethod(), requestURI);
			String newURI = requestURI.substring(0, requestURI.length() - 1);
			request.getRequestDispatcher(newURI).forward(request, response);
		} else {
			filterChain.doFilter(request, response);
		}
	}
}