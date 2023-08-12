package com.taptech.sse.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationEvent implements Serializable {
	private static final long serialVersionUID = 1182244805088378187L;

	private String eventId;
	private String eventType;
	private String clientId;
	private String notificationId;
	private String message;
	private String correlationId;
	private String requestedDateTime;
}
