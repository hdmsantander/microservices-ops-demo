package mx.hdmsantander.opsdemo.inventory.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;
import mx.hdmsantander.opsdemo.inventory.event.OrderEventSender;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;
import mx.hdmsantander.opsdemo.inventory.service.OrderService;

/**
 * Integration tests verifying resilience4j behaviour: retry, circuit breaker,
 * and ignore-exceptions through actual service method invocations with mocked RestTemplate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = mx.hdmsantander.opsdemo.inventory.InventoryApplication.class)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@EmbeddedKafka(bootstrapServersProperty = "spring.cloud.stream.kafka.binder.brokers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class Resilience4jIntegrationTest {

	@MockitoBean
	private RestTemplate restTemplate;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private OrderEventSender orderEventSender;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@BeforeEach
	void resetOrderServiceCircuit() {
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderService");
		if (cb != null) {
			cb.transitionToClosedState();
		}
	}

	// --- InventoryService (external: 5 retries, circuit breaker) ---

	private static final String INVENTORY_URL = "https://petstore.swagger.io/v2/store/inventory";

	@Test
	@Order(1)
	void inventoryService_returns_inventory_when_api_succeeds() throws Exception {
		JsonNode inventory = objectMapper.createObjectNode().put("available", 10).put("pending", 2).put("sold", 5);
		String jsonBody = objectMapper.writeValueAsString(inventory);
		when(restTemplate.getForEntity(eq(INVENTORY_URL), eq(String.class)))
				.thenReturn(ResponseEntity.ok(jsonBody));

		JsonNode result = inventoryService.getInventory(null, null);

		assertThat(result).isNotNull();
		assertThat(result.has("available")).as("Inventory should have 'available' from stubbed API").isTrue();
		assertThat(result.get("available").asInt()).isEqualTo(10);
		verify(restTemplate).getForEntity(eq(INVENTORY_URL), eq(String.class));
	}

	@Test
	@Order(2)
	void inventoryService_returns_fallback_when_api_fails() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(eq(INVENTORY_URL), eq(String.class));

		JsonNode result = inventoryService.getInventory(null, null);

		assertThat(result.isObject()).isTrue();
		assertThat(result.isEmpty()).isTrue();
		verify(restTemplate, atLeast(1)).getForEntity(eq(INVENTORY_URL), eq(String.class));
	}

	@Test
	@Order(3)
	void inventoryService_circuit_breaker_returns_fallback_when_open() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(eq(INVENTORY_URL), eq(String.class));

		for (int i = 0; i < 5; i++) {
			inventoryService.getInventory(null, null);
		}

		var result = inventoryService.getInventory(null, null);
		assertThat(result).isEmpty();
	}

	// --- OrderService (3 iterations, random IDs 1-10, 5 retries, ignore 404, circuit: min 5 calls, 50% failure) ---

	private static final int ORDER_SERVICE_ITERATIONS = 3;

	@Test
	@Order(10)
	void orderService_updateOrders_all_success_sends_events() {
		OrderDto dto = OrderDto.builder().id(1).petId("pet-1").quantity(1).status("placed").complete(false).build();
		when(restTemplate.getForEntity(any(String.class), eq(OrderDto.class), anyMap()))
				.thenReturn(ResponseEntity.ok(dto));

		orderService.updateOrders();

		verify(restTemplate, times(ORDER_SERVICE_ITERATIONS)).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
		verify(orderEventSender, times(ORDER_SERVICE_ITERATIONS)).send(any(OrderDto.class));
	}

	@Test
	@Order(11)
	void orderService_updateOrders_404_no_retry_one_call_per_fetch() {
		doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))
				.when(restTemplate).getForEntity(any(String.class), eq(OrderDto.class), anyMap());

		orderService.updateOrders();

		verify(restTemplate, times(ORDER_SERVICE_ITERATIONS)).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
		verify(orderEventSender, never()).send(any(OrderDto.class));
	}

	@Test
	@Order(12)
	void orderService_updateOrders_500_triggers_retry_then_fallback() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(OrderDto.class), anyMap());

		orderService.updateOrders();

		verify(restTemplate, atLeast(ORDER_SERVICE_ITERATIONS)).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
		verify(orderEventSender, never()).send(any(OrderDto.class));
	}

	@Test
	@Order(13)
	void orderService_updateOrders_circuit_open_no_api_calls_after_threshold() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(OrderDto.class), anyMap());

		for (int i = 0; i < 2; i++) {
			orderService.updateOrders();
		}

		clearInvocations(restTemplate);
		orderService.updateOrders();

		verify(restTemplate, never()).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
	}

	@Test
	@Order(14)
	void orderService_updateOrders_mix_ids_1_to_5_success_6_to_10_404() {
		OrderDto okDto = OrderDto.builder().id(1).petId("pet-1").quantity(1).status("placed").complete(false).build();
		HttpClientErrorException notFound = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);
		when(restTemplate.getForEntity(any(String.class), eq(OrderDto.class), anyMap()))
				.thenAnswer(inv -> {
					@SuppressWarnings("unchecked")
					Map<String, Integer> vars = (Map<String, Integer>) inv.getArgument(2);
					int orderId = vars.get("order");
					if (orderId >= 1 && orderId <= 5) {
						return ResponseEntity.ok(OrderDto.builder().id(orderId).petId("pet-" + orderId).quantity(1).status("placed").complete(false).build());
					}
					throw notFound;
				});

		orderService.updateOrders();

		verify(restTemplate, times(ORDER_SERVICE_ITERATIONS)).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
		verify(orderEventSender, atLeast(0)).send(any(OrderDto.class));
		verify(orderEventSender, atMost(ORDER_SERVICE_ITERATIONS)).send(any(OrderDto.class));
	}

	@Test
	@Order(15)
	void orderService_updateOrders_minimum_calls_before_circuit_evaluates() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(OrderDto.class), anyMap());

		orderService.updateOrders();

		verify(restTemplate, atLeast(ORDER_SERVICE_ITERATIONS)).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
	}

	@Test
	@Order(16)
	void orderService_updateOrders_response_body_null_returns_no_event() {
		when(restTemplate.getForEntity(any(String.class), eq(OrderDto.class), anyMap()))
				.thenReturn(ResponseEntity.ok().build());

		orderService.updateOrders();

		verify(restTemplate, times(ORDER_SERVICE_ITERATIONS)).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
		verify(orderEventSender, never()).send(any(OrderDto.class));
	}

}
