package mx.hdmsantander.opsdemo.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private OrderService orderService;

	@Mock
	private Tracer tracer;

	private InventoryService inventoryService;
	private ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		inventoryService = new InventoryService();
		ReflectionTestUtils.setField(inventoryService, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(inventoryService, "objectMapper", objectMapper);
		ReflectionTestUtils.setField(inventoryService, "orderService", orderService);
		ReflectionTestUtils.setField(inventoryService, "tracer", tracer);
		ReflectionTestUtils.setField(inventoryService, "meterRegistry", new SimpleMeterRegistry());
	}

	@Test
	void getInventory_withStatusFilter_returnsFilteredNode() throws Exception {
		String json = "{\"available\":10,\"pending\":2,\"sold\":5}";
		when(restTemplate.getForEntity(any(String.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok(json));

		JsonNode result = inventoryService.getInventory("available", null);

		assertThat(result).isNotNull();
		assertThat(result.has("available")).isTrue();
		assertThat(result.get("available").asInt()).isEqualTo(10);
		assertThat(result.has("pending")).isFalse();
		verify(restTemplate).getForEntity(any(String.class), eq(String.class));
	}

	@Test
	void getInventory_withLowStockFilter_recordsLowStockItems() throws Exception {
		String json = "{\"available\":3,\"pending\":10,\"sold\":50}";
		when(restTemplate.getForEntity(any(String.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok(json));

		JsonNode result = inventoryService.getInventory(null, 5);

		assertThat(result).isNotNull();
		assertThat(result.has("available")).isTrue();
		assertThat(result.get("available").asInt()).isEqualTo(3);
		verify(restTemplate).getForEntity(any(String.class), eq(String.class));
	}

	@Test
	void getInventory_emptyBody_returnsEmptyNode() {
		when(restTemplate.getForEntity(any(String.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok(""));

		JsonNode result = inventoryService.getInventory(null, null);

		assertThat(result).isNotNull();
		assertThat(result.isEmpty()).isTrue();
	}

	@Test
	void refreshInventoryAndOrders_updatesOrdersAndReturnsInventory() throws Exception {
		String json = "{\"available\":5,\"pending\":1}";
		when(restTemplate.getForEntity(any(String.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok(json));

		JsonNode result = inventoryService.refreshInventoryAndOrders();

		assertThat(result).isNotNull();
		assertThat(result.get("available").asInt()).isEqualTo(5);
		verify(orderService).updateOrders();
		verify(restTemplate).getForEntity(any(String.class), eq(String.class));
	}
}
