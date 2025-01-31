package it.eng.dome.billing.scheduler.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

public class StartRequestDTO {
	
	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant datetime;

    public Instant getDatetime() {
        return datetime;
    }

    public void setDatetime(Instant datetime) {
        this.datetime = datetime;
    }

}
