package com.taptech.sse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;

@SpringBootApplication(exclude = {RedisReactiveAutoConfiguration.class, RedisAutoConfiguration.class})
public class SpringSseDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringSseDemoApplication.class, args);
	}

}
