package mindustrytool.utils;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonUtils {
    public static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public static String toJsonString(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (IOException e) {
            throw new RuntimeException("Can not parse to json: " + e.getMessage(), e);
        }
    }

    public static <T> T readJsonAsClass(String data, Class<T> clazz) throws JsonParseException, JsonMappingException, IOException {
            return objectMapper.readValue(data, clazz);
    }
    public static JsonNode readJson(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (IOException e) {
            throw new RuntimeException("Can not parse to json: " + e.getMessage(), e);
        }
    }
}
