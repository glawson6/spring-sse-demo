package com.taptech.sse.config;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@ConfigurationProperties(prefix = "sse")
public class SSEProperties {
	public static final String TOPIC = "taptech-sse-test-topic";
	//public static final String BOOTSTRAP_SERVERS = "172.28.1.93:9093";
	public static final String BOOTSTRAP_SERVERS = "localhost:9093";
	public static final String GROUP_ID_CONFIG = "cg-taptech-new";
	public static final String CLIENT_ID_CONFIG = "consumer-taptech-new";

	public static final Long DEFAULT_HOLD_SECONDS = 120L;
	@Builder.Default
	Long clientHoldSeconds = DEFAULT_HOLD_SECONDS;
	@Builder.Default
	Long topicRetryDelaySeconds = DEFAULT_HOLD_SECONDS;


}
