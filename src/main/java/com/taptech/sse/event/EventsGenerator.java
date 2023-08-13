package com.taptech.sse.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.taptech.sse.utils.ObjectMapperFactory;
import com.taptech.sse.config.SSEProperties;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.taptech.sse.config.SSEProperties.CLIENT_ID_CONFIG;
import static com.taptech.sse.config.SSEProperties.TOPIC;


public class EventsGenerator {

    private static final Logger logger = LoggerFactory.getLogger(EventsGenerator.class);

    private static final String idsPath = "classpath:client-ids.json";

    private KafkaSender<String, String> sender;
    private SimpleDateFormat dateFormat;
    Environment env;

    private static ResourceLoader resourceLoader = new DefaultResourceLoader();
    private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper(ObjectMapperFactory.Scope.SINGLETON);


    public EventsGenerator(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {

        String servers = env.getProperty("spring.kafka.boostrap.servers", SSEProperties.BOOTSTRAP_SERVERS);

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID_CONFIG);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        SenderOptions<String, String> senderOptions = SenderOptions.create(props);
        sender = KafkaSender.create(senderOptions);

        dateFormat = new SimpleDateFormat("HH:mm:ss:SSS z dd MMM yyyy");
    }

    static CollectionType idListType = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);

    public List<String> getKeyIds() {
        List<String> keyIds = new ArrayList<>();
        try {
            Resource resource = resourceLoader.getResource(idsPath);
            String json = IOUtils.toString(resource.getInputStream(), Charset.defaultCharset()).trim();
            List<String> ids = objectMapper.readValue(json, idListType);
            keyIds.addAll(ids);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return keyIds;
    }

    public void close() {
        sender.close();
    }

    public Flux<String> generateMessages(List<String> ids) {

        String topic = TOPIC;
        return sender.<Long>send(Flux.fromIterable(ids)
                        .index()
                        .map(id -> SenderRecord.create(new ProducerRecord<>(topic, id.getT2(), generateMessage(id.getT2(), System.currentTimeMillis(), id.getT1())), id.getT1())))
                .doOnError(e -> logger.error("Send failed", e))
                .doOnNext(r -> {
                    RecordMetadata metadata = r.recordMetadata();
                    System.out.printf("Message %d sent successfully, topic-partition=%s-%d offset=%d timestamp=%s%n",
                            r.correlationMetadata(),
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset(),
                            dateFormat.format(new Date(metadata.timestamp())));

                })
                .flatMap(sr -> Flux.fromIterable(ids));
    }

    Flux<Tuple2<SenderResult<Long>, NotificationEvent>> getSenderResults(NotificationEvent event) {
        return sender.<Long>send(Mono.just(event)
                        .map(evt -> SenderRecord.create(generateProducerRecord(evt), Long.getLong(evt.getEventId()))))
                .map(senderRecord -> Tuples.of(senderRecord, event));
    }

    public Flux<NotificationEvent> generateMessages2(List<String> ids) {

        String topic = TOPIC;
        return Flux.fromIterable(ids)
                .index()
                .map(tuple -> generateNotificationEvent(tuple.getT2(), tuple.getT1(), System.currentTimeMillis()))
                .flatMap(event -> getSenderResults(event))
                .doOnError(e -> logger.error("Send failed", e))
                .doOnNext(tuple -> {
                    RecordMetadata metadata = tuple.getT1().recordMetadata();
                    System.out.printf("Message %d sent successfully, topic-partition=%s-%d offset=%d timestamp=%s%n",
                            tuple.getT1().correlationMetadata(),
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset(),
                            dateFormat.format(new Date(metadata.timestamp())));

                })
                .map(tuple -> tuple.getT2());
    }

    private ProducerRecord<String, String> generateProducerRecord(NotificationEvent event) {
        return new ProducerRecord<>(TOPIC, event.getClientId(), notificationEventToString(event));
    }

    private String notificationEventToString(NotificationEvent event) {
        String messageStr = "";
        try {
            messageStr = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return messageStr;
    }

    private NotificationEvent generateNotificationEvent(String clientId, Long eventId, Long correlationId) {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(eventId.toString())
                .eventType("notification-event")
                .correlationId(correlationId.toString())
                .clientId(clientId)
                .build();
        return event;
    }

    public Flux<String> generateMessages() {

        String topic = TOPIC;
        List<String> ids = getKeyIds();
        return generateMessages(ids);
    }

    public Flux<NotificationEvent> generateMessages2() {

        String topic = TOPIC;
        List<String> ids = getKeyIds();
        return generateMessages2(ids);
    }

    private String generateMessage(String clientId, Long correlationId, Long eventId) {
        String messageStr = "";
        NotificationEvent event = NotificationEvent.builder()
                .eventId(eventId.toString())
                .eventType("workspace-event")
                .correlationId(correlationId.toString())
                .clientId(clientId)
                .build();
        try {
            messageStr = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return messageStr;
    }

}
