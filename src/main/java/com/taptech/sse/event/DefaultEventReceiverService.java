package com.taptech.sse.event;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taptech.sse.utils.DurationSupplier;
import com.taptech.sse.utils.ObjectMapperFactory;
import com.taptech.sse.config.SSEProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

public class DefaultEventReceiverService implements EventReceiverService {
	private static final Logger logger = LoggerFactory.getLogger(DefaultEventReceiverService.class);

	public final static String TEST_STREAM = "test.stream";
	public final static String TEST_STREAM_KEY = "test.stream.key";
	public final static String TEST_STREAM_VALUE = "test.stream.value";
	private static final String EMPTY_STR = "";
	public static final String CLIENT_STREAM_STARTED = "client.stream.started";
	public static final String HASH_PREFIX = "hash.";

	private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper(ObjectMapperFactory.Scope.SINGLETON);

	ReactiveStringRedisTemplate redisTemplate;
	KafkaReceiver<String, String> kafkaReceiver;
	SSEProperties sseProperties;
	StreamReadOptions streamReadOptions;

	public DefaultEventReceiverService(ReactiveStringRedisTemplate redisTemplate, KafkaReceiver<String, String> kafkaReceiver,
									   SSEProperties sseProperties) {
		this.redisTemplate = redisTemplate;
		this.kafkaReceiver = kafkaReceiver;
		this.sseProperties = sseProperties;
		this.streamReadOptions = StreamReadOptions.empty().autoAcknowledge()
				.block(Duration.of(sseProperties.getClientHoldSeconds(), ChronoUnit.SECONDS));
	}

	static final Function<String,String> calculateHashKey = str -> new StringBuilder(HASH_PREFIX).append(str).toString();

	@PostConstruct
	public void init() {

		this.redisTemplate.opsForValue().append(TEST_STREAM_KEY, TEST_STREAM_VALUE).subscribe();

		/*
		ObjectRecord<String, String> record = StreamRecords.newRecord()
			.ofObject(TEST_STREAM_VALUE)
			//.withStreamKey(recRecord.key());
			.withStreamKey(TEST_STREAM_KEY);
		this.redisTemplate.opsForStream().add(record)
				.subscribe(recordId -> logger.info("################# Got recordId => {}",recordId.toString()));

		this.redisTemplate.opsForStream().read(String.class,StreamOffset.fromStart(TEST_STREAM_KEY))
				.subscribe(objrec -> logger.info("################### Read value => {}",objrec.getValue()));

		 */

	}

	@EventListener(ApplicationStartedEvent.class)
	public Disposable startKafkaConsumer() {
		logger.info("############# Starting Kafka listener.....");
		return kafkaReceiver.receive()
				.doOnError(error -> logger.error("Error receiving event, will retry", error))
				.retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(sseProperties.getTopicRetryDelaySeconds())))
				.doOnNext(record -> logger.info("Received event: key {}", record.key()))
				.filterWhen(record -> checkIfStreamBeingAccessed(record))
				.concatMap(this::handleEvent)
				.subscribe(record -> record.receiverOffset().acknowledge());
	}

	Mono<Boolean> checkIfStreamBeingAccessed(ReceiverRecord<String,String> record){
		return this.redisTemplate.opsForHash().hasKey(calculateHashKey.apply(record.key()), CLIENT_STREAM_STARTED)
				.doOnNext(val -> logger.info("key => {}'s stream is being accessed {}",record.key(),val));
	}

	public Mono<ReceiverRecord<String, String>> handleEvent(ReceiverRecord<String, String> record) {
		return Mono.just(record)
				.flatMap(this::produce)
				.doOnError(ex -> logger.warn("Error processing event: key {}", record.key(), ex))
				.onErrorResume(ex -> Mono.empty())
				.doOnNext(rec -> logger.debug("Successfully processed event: key {}", record.key()))
				.then(Mono.just(record));
	}

	public Mono<Tuple2<RecordId, ReceiverRecord<String, String>>> produce(ReceiverRecord<String, String> recRecord) {

		ObjectRecord<String, String> record = StreamRecords.newRecord()
				.ofObject(recRecord.value())
				.withStreamKey(recRecord.key());
		//.withStreamKey("workspaces");
		return this.redisTemplate.opsForStream().add(record)
				.map(recId -> Tuples.of(recId, recRecord));
	}

	Function<ObjectRecord<String, String>, NotificationEvent> convertToNotificationEvent() {
		return (record) -> {
			NotificationEvent event = null;
			try {
				event = objectMapper.readValue(record.getValue(), NotificationEvent.class);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
				event = new NotificationEvent();
			}
			return event;
		};
	}

	private Mono<String> createGroup(String workspaceId){
		return redisTemplate.getConnectionFactory().getReactiveConnection().streamCommands()
				.xGroupCreate(ByteBuffer.wrap(workspaceId.getBytes()), workspaceId, ReadOffset.from("0-0"), true)
				.doOnError((error) -> {
					if (logger.isDebugEnabled()){
						logger.debug("Could not create group.",error);
					}
				})
				.map(okStr -> workspaceId)
				.onErrorResume((error) -> Mono.just(workspaceId));
	}

	/*
	public Flux<WorkspaceEvent> consume(String workspaceId) {

		StreamOffset<String> streamOffset = StreamOffset.create(workspaceId, ReadOffset.lastConsumed());

		Consumer consumer = Consumer.from(workspaceId, workspaceId);

		try {
			redisTemplate.getConnectionFactory().getReactiveConnection().streamCommands()
					.xGroupCreate(ByteBuffer.wrap(workspaceId.getBytes()), workspaceId, ReadOffset.from("0-0"), true)
					.subscribe(str -> logger.info("Group created => {}", str));
		} catch (Exception e) {
			if (logger.isDebugEnabled()){
				logger.debug("Could not create group.",e);
			}
		}

		DurationSupplier booleanSupplier = new DurationSupplier(Duration.of(sseProperties.getClientHoldSeconds(), ChronoUnit.SECONDS), LocalDateTime.now());


		return this.redisTemplate.opsForStream().read(String.class, consumer, streamReadOptions, streamOffset)
				.map(convertToWorkspaceEvent())
				.repeat(booleanSupplier);
	}

	 */

	private Flux<NotificationEvent> findClientNotificationEvents(Consumer consumer, StreamOffset<String> streamOffset, DurationSupplier booleanSupplier){
		return this.redisTemplate.opsForStream().read(String.class, consumer, streamReadOptions, streamOffset)
				.map(convertToNotificationEvent())
				.repeat(booleanSupplier);
	}

	public Flux<NotificationEvent> consume(final String clientId){
		return Flux.from(createGroup(clientId))
				.flatMap(id -> addIdToStream(clientId))
				.map(id -> Tuples.of(StreamOffset.create(clientId, ReadOffset.lastConsumed()),
						Consumer.from(clientId, clientId),
						new DurationSupplier(Duration.of(sseProperties.getClientHoldSeconds(), ChronoUnit.SECONDS), LocalDateTime.now())))
				.flatMap(tuple3 -> findClientNotificationEvents(tuple3.getT2(), tuple3.getT1(), tuple3.getT3()))
				.doAfterTerminate(new Runnable() {
					@Override
					public void run() {
						try{
							redisTemplate.opsForHash().delete(calculateHashKey.apply(clientId)).subscribe();
						} catch (Exception e){
							logger.warn("Could not delete clientId");
						}

					}
				});

	}

	private Mono<String> addIdToStream(String id) {
		return this.redisTemplate.opsForHash().put(calculateHashKey.apply(id), CLIENT_STREAM_STARTED, Boolean.TRUE.toString()).map(val -> id);
	}

	public Flux<Boolean> deleteWorkspaceStream(String clientId){
		StreamOffset<String> streamOffset = StreamOffset.create(clientId, ReadOffset.lastConsumed());
		StreamReadOptions streamReadOptions = StreamReadOptions.empty().noack();
		Consumer consumer = Consumer.from(clientId, clientId);

		return this.redisTemplate.opsForStream().read(String.class, consumer, streamReadOptions, streamOffset)
				.flatMap(objRecord -> this.redisTemplate.opsForStream().delete(clientId,objRecord.getId()).map(val -> objRecord))
				.flatMap(objRecord -> this.redisTemplate.opsForHash().delete(clientId));
	}

	@Override
	public Flux<String> consumeString(String clientId) {
		return this.redisTemplate.opsForStream().read(String.class, StreamOffset.latest(clientId)).map(ObjectRecord::getValue);
	}

}
