package mx.hdmsantander.opsdemo.inventory.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Tracer;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;

/**
 * Verifies that trace context is propagated in the bean context and that
 * spans are created and sent when handling HTTP requests. Asserts that
 * X-Zipkin-Trace-Id header is present in responses, confirming trace
 * propagation through the request/response cycle.
 * Uses HTTP transport for Zipkin (no Kafka client) and SAME_THREAD execution.
 * Uses embedded Kafka for Spring Cloud Stream (no external Kafka required).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@EmbeddedKafka(bootstrapServersProperty = "spring.cloud.stream.kafka.binder.brokers")
class TracingPropagationTest {

	@Autowired
	private Tracer tracer;

	@Autowired
	private TestRestTemplate restTemplate;

	@MockitoBean
	InventoryService inventoryService;

	@Test
	void tracer_is_present_in_context() {
		assertThat(tracer).isNotNull();
	}

	@Test
	void trace_context_is_propagated_in_http_response() {
		JsonNode emptyInventory = new ObjectMapper().createObjectNode();
		when(inventoryService.getInventory()).thenReturn(emptyInventory);

		ResponseEntity<String> response = restTemplate.getForEntity("/v1/inventory", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		String traceIdHeader = response.getHeaders().getFirst("X-Zipkin-Trace-Id");
		assertThat(traceIdHeader)
				.as("X-Zipkin-Trace-Id header should be present when tracing is active")
				.isNotNull()
				.contains("Id=")
				.contains("Parent=")
				.contains("Sampled=");
	}

	@Test
	void tracer_creates_span_context_when_active() {
		JsonNode emptyInventory = new ObjectMapper().createObjectNode();
		when(inventoryService.getInventory()).thenReturn(emptyInventory);

		ResponseEntity<String> response = restTemplate.getForEntity("/v1/inventory", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getFirst("X-Zipkin-Trace-Id")).isNotNull();
	}
}
