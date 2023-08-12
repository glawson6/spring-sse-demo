package com.taptech.sse.event;

import reactor.core.publisher.Flux;

public interface EventReceiverService {
	Flux<NotificationEvent> consume(String workspaceId);

	Flux<String> consumeString(String workspaceId);
}
