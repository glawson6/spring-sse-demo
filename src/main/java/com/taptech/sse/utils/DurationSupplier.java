package com.taptech.sse.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;

public class DurationSupplier implements BooleanSupplier {

	private static final Logger logger = LoggerFactory.getLogger(DurationSupplier.class);

	Duration duration;
	LocalDateTime startTime;

	public DurationSupplier(Duration duration, LocalDateTime startTime) {
		this.duration = duration;
		this.startTime = startTime;
	}

	@Override
	public boolean getAsBoolean() {
		boolean asBoolean = LocalDateTime.now().minusSeconds(duration.getSeconds()).isBefore(startTime);
		logger.debug("Checking LocalDateTime.now().minusSeconds(duration.getSeconds()).isBefore(startTime) => {}",asBoolean);
		return asBoolean;
	}
}
