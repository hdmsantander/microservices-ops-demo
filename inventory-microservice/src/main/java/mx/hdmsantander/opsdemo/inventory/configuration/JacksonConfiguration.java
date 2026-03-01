package mx.hdmsantander.opsdemo.inventory.configuration;

import java.io.IOException;
import java.util.Collections;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configures ObjectMapper and RestTemplate for JSON handling.
 * Uses RestTemplateBuilder for automatic trace context propagation (B3 headers).
 * Provides JsonNodeHttpMessageConverter for deserializing JSON to JsonNode.
 * Uses JacksonJsonHttpMessageConverter (Jackson 3) for other JSON types.
 */
@Configuration
public class JacksonConfiguration {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder, ObjectMapper objectMapper) {
		return builder
				.additionalMessageConverters(
						new StringHttpMessageConverter(),
						new JsonNodeHttpMessageConverter(objectMapper),
						new JacksonJsonHttpMessageConverter())
				.build();
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
