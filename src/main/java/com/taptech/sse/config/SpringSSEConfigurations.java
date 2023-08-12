package com.taptech.sse.config;

import com.taptech.sse.event.DefaultEventReceiverService;
import com.taptech.sse.event.EventReceiverService;
import com.taptech.sse.event.EventsGenerator;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.internals.ConsumerFactory;
import reactor.kafka.receiver.internals.DefaultKafkaReceiver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


@Configuration
@EnableConfigurationProperties({CacheConfigProperties.class, SSEProperties.class})
public class SpringSSEConfigurations {
	private static final Logger logger = LoggerFactory.getLogger(SpringSSEConfigurations.class);

	@Autowired
	Environment env;


	private RedisNode populateNode(String host, Integer port) {
		return new RedisNode(host, port);
	}

	@Bean
	public RedisTemplate<?, ?> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<byte[], byte[]> template = new RedisTemplate();
		template.setConnectionFactory(redisConnectionFactory);
		return template;
	}

	@Bean
	RedisStandaloneConfiguration RedisStandaloneConfiguration(CacheConfigProperties cacheConfigProperties){

		RedisPassword redisPassword = RedisPassword.of(cacheConfigProperties.getPassword());
		RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
		redisStandaloneConfiguration.setHostName(cacheConfigProperties.getHost());
		redisStandaloneConfiguration.setPort(cacheConfigProperties.getPort());
		redisStandaloneConfiguration.setPassword(redisPassword);
		return redisStandaloneConfiguration;
	}

	@Bean
	public ReactiveRedisConnectionFactory connectionFactory() {
		String host = env.getProperty("REDIS_HOST","localhost");
		return new LettuceConnectionFactory(host, 6379);
	}

	@Bean
	@ConditionalOnMissingBean(
			name = {"reactiveRedisTemplate"}
	)
	@ConditionalOnBean({ReactiveRedisConnectionFactory.class})
	public ReactiveRedisTemplate<Object, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory reactiveRedisConnectionFactory, ResourceLoader resourceLoader) {
		JdkSerializationRedisSerializer jdkSerializer = new JdkSerializationRedisSerializer(resourceLoader.getClassLoader());
		RedisSerializationContext<Object, Object> serializationContext = RedisSerializationContext.newSerializationContext().key(jdkSerializer).value(jdkSerializer).hashKey(jdkSerializer).hashValue(jdkSerializer).build();
		return new ReactiveRedisTemplate(reactiveRedisConnectionFactory, serializationContext);
	}

	@Bean("reactiveStringRedisTemplate")
	@ConditionalOnMissingBean(
			name = {"reactiveStringRedisTemplate"}
	)
	//@ConditionalOnBean({ReactiveRedisConnectionFactory.class})
	public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
		return new ReactiveStringRedisTemplate(reactiveRedisConnectionFactory);
	}

	@Bean
    public KafkaReceiver kafkaReceiver(){

		String servers = env.getProperty("spring.kafka.boostrap.servers",SSEProperties.BOOTSTRAP_SERVERS);
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, SSEProperties.CLIENT_ID_CONFIG);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, SSEProperties.GROUP_ID_CONFIG);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        return new DefaultKafkaReceiver(ConsumerFactory.INSTANCE, ReceiverOptions.create(props).subscription(Collections.singleton(SSEProperties.TOPIC)));
    }

	@Bean
	EventReceiverService eventReceiverService(@Qualifier("reactiveStringRedisTemplate")ReactiveStringRedisTemplate redisTemplate,
											  KafkaReceiver<String,String> kafkaReceiver, SSEProperties sseProperties){
		return new DefaultEventReceiverService(redisTemplate, kafkaReceiver,sseProperties);
	}

	@Bean
    EventsGenerator serverGenerateEventsApplication(){
		return new EventsGenerator(env);
	}
}
