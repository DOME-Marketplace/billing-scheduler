package it.eng.dome.billing.scheduler.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import it.eng.dome.billing.scheduler.validator.ValidationIssue;

public class BillingSchedulerValidationException extends Exception {

	private static final long serialVersionUID = 1L;
	
	private final List<ValidationIssue> issues;

    public BillingSchedulerValidationException(List<ValidationIssue> issues) {
        super(buildMessage(issues));
        this.issues = issues;
    }
    
    public BillingSchedulerValidationException(ValidationIssue issue) {
        super(buildMessage(issue));
        this.issues = new ArrayList<ValidationIssue>();
        this.issues.add(issue);
    }
    

    public List<ValidationIssue> getIssues() {
        return issues;
    }

    private static String buildMessage(List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "Validation successful: no issues provided";
        }

        String joinedIssues = issues.stream()
                .map(issue -> " - " + issue.toString())
                .collect(Collectors.joining("\n"));

        return "Validation failed:\n" + joinedIssues;
    }
    
    private static String buildMessage(ValidationIssue issue) {
    	
    	if (issue == null ) {
            return "Validation successful: no issue provided";
        }

        return "Validation failed:\n" + issue.toString();
    }
}

