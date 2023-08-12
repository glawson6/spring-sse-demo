package com.taptech.sse.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public interface ObjectMapperFactory {


    public enum Scope{
        SINGLETON, PROTOTYPE;
    }

    static final ObjectMapper INSTANCE = createAnObjectMapper();

    private static ObjectMapper createAnObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
        return objectMapper;
    }

    public static  ObjectMapper createObjectMapper(Scope scope) {
        if (scope.equals(Scope.PROTOTYPE)){
            return createAnObjectMapper();
        } else {
            return INSTANCE;
        }
    }

}
