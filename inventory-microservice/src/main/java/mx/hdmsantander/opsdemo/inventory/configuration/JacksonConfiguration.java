package mx.hdmsantander.opsdemo.inventory.configuration;

import java.io.IOException;
import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configures Jackson ObjectMapper and RestTemplate for JSON handling.
 * Provides an ObjectMapper bean and a RestTemplate that can deserialize
 * JSON responses to JsonNode via a custom message converter.
 */
@Configuration
public class JacksonConfiguration {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	public RestTemplate restTemplate(ObjectMapper objectMapper) {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getMessageConverters().add(0, new JsonNodeHttpMessageConverter(objectMapper));
		return restTemplate;
	}

	/**
	 * HttpMessageConverter that deserializes JSON to JsonNode using ObjectMapper.readTree().
	 * JsonNode is abstract; Jackson cannot deserialize directly into it without this converter.
	 */
	private static final class JsonNodeHttpMessageConverter implements HttpMessageConverter<JsonNode> {

		private final ObjectMapper objectMapper;

		JsonNodeHttpMessageConverter(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
		}

		@Override
		public boolean canRead(Class<?> clazz, MediaType mediaType) {
			return mediaType != null && mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)
					&& JsonNode.class.isAssignableFrom(clazz);
		}

		@Override
		public boolean canWrite(Class<?> clazz, MediaType mediaType) {
			return mediaType != null && mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)
					&& JsonNode.class.isAssignableFrom(clazz);
		}

		@Override
		public java.util.List<MediaType> getSupportedMediaTypes() {
			return Collections.singletonList(MediaType.APPLICATION_JSON);
		}

		@Override
		public JsonNode read(Class<? extends JsonNode> clazz, HttpInputMessage inputMessage)
				throws IOException, HttpMessageNotReadableException {
			try (java.io.InputStream body = inputMessage.getBody()) {
				return body != null ? objectMapper.readTree(body) : objectMapper.createObjectNode();
			}
		}

		@Override
		public void write(JsonNode node, MediaType contentType, HttpOutputMessage outputMessage)
				throws IOException, HttpMessageNotWritableException {
			objectMapper.writeValue(outputMessage.getBody(), node);
		}
	}
}
