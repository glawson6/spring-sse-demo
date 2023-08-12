package com.taptech.sse.controller;

import com.taptech.sse.event.EventReceiverService;
import com.taptech.sse.event.EventsGenerator;
import com.taptech.sse.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

import java.util.List;

@RestController
@RequestMapping(path = "/sse")
public class SSEController {

	private static final Logger logger = LoggerFactory.getLogger(SSEController.class);


	@Autowired
    KafkaReceiver<String,String> kafkaReceiver;

	@Autowired
	EventReceiverService service;

	@Autowired
	EventsGenerator generateEvents;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<NotificationEvent>> getEventsFlux(@RequestParam String clientId){
        return service.consume(clientId)
				.map(event ->  ServerSentEvent.<NotificationEvent> builder()
						.id(clientId)
						.event(event.getEventId())
						.data(event)
						.build());
    }


	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/generate")
	Mono<ResponseEntity<List<String>>> getEventsFlux(){
		return generateEvents.generateMessages()
				.collectList()
				.map(list -> ResponseEntity.ok(list));
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/generateNE")
	Mono<ResponseEntity<List<NotificationEvent>>> getNEventsFlux(){
		return generateEvents.generateMessages2()
				.collectList()
				.map(list -> ResponseEntity.ok(list));
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/generateWithIds")
	Mono<ResponseEntity<List<String>>> getEventsFluxWithId(@RequestBody List<String> ids) {
		return generateEvents.generateMessages(ids)
				.collectList()
				.map(list -> ResponseEntity.ok(list));
	}

	/*
	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<String>> getEventsFlux(@RequestParam String workspaceId){
		Flux<ReceiverRecord<String,String>> kafkaFlux = kafkaReceiver.receive();
		return kafkaFlux.checkpoint("Messages are started being consumed")
				.log()
				.filter(record ->  record.key().equals(workspaceId))
				.doOnNext(r -> r.receiverOffset().acknowledge())
				//.map(ReceiverRecord::value)
				.checkpoint("Messages are done consumed")
				.map(record -> ServerSentEvent.<String> builder()
						.id(record.key())
						.event("workspace-event")
						.data(record.value())
						.build());
	}

	 */

	/*private static ObjectMapper objectMapper = new DefaultObjectMapperFactory().createObjectMapper(ObjectMapperFactory.Scope.PROTOTYPE);


	@PostConstruct
	public void init(){
		int count = 20;
		List<String> uuids = new ArrayList<>(count);
		for (int x = 0; x <= count; ++x ){
			uuids.add(UUID.randomUUID().toString());
		}
		try {
			logger.info("{}",objectMapper.writeValueAsString(uuids));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}
*/
 }
