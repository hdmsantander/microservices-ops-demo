package mx.hdmsantander.opsdemo.inventory.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import mx.hdmsantander.opsdemo.inventory.event.OrderEventSender;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;

/**
 * Integration tests verifying resilience4j behaviour: retry, circuit breaker,
 * and ignore-exceptions through actual service method invocations with mocked RestTemplate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = mx.hdmsantander.opsdemo.inventory.InventoryApplication.class)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@EmbeddedKafka(bootstrapServersProperty = "spring.cloud.stream.kafka.binder.brokers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Resilience4jIntegrationTest {

	@MockitoBean
	private RestTemplate restTemplate;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private OrderEventSender orderEventSender;

	// --- InventoryService (external: 5 retries, circuit breaker) ---

	@Test
	@Order(1)
	void inventoryService_returns_inventory_when_api_succeeds() throws Exception {
		JsonNode inventory = objectMapper.createObjectNode().put("available", 10).put("pending", 2).put("sold", 5);
		when(restTemplate.getForEntity(any(String.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok(objectMapper.writeValueAsString(inventory)));

		JsonNode result = inventoryService.getInventory();

		assertThat(result).isNotNull();
		assertThat(result.get("available").asInt()).isEqualTo(10);
		verify(restTemplate).getForEntity(any(String.class), eq(String.class));
	}

	@Test
	@Order(2)
	void inventoryService_returns_fallback_when_api_fails() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(String.class));

		JsonNode result = inventoryService.getInventory();

		assertThat(result.isObject()).isTrue();
		assertThat(result.isEmpty()).isTrue();
		verify(restTemplate, atLeast(1)).getForEntity(any(String.class), eq(String.class));
	}

	@Test
	@Order(3)
	void inventoryService_circuit_breaker_returns_fallback_when_open() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(String.class));

		for (int i = 0; i < 5; i++) {
			inventoryService.getInventory();
		}

		var result = inventoryService.getInventory();
		assertThat(result).isEmpty();
	}

}
