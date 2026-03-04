package mx.hdmsantander.opsdemo.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

	@Mock
	private RestTemplate restTemplate;

	private InventoryService inventoryService;
	private ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		inventoryService = new InventoryService();
		ReflectionTestUtils.setField(inventoryService, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(inventoryService, "objectMapper", objectMapper);
		ReflectionTestUtils.setField(inventoryService, "inventoryBaseUrl", "http://localhost:8085");
	}

	@Test
	void getInventory_withStatus_addsParamToUrl() throws Exception {
		String json = "{\"available\":10,\"pending\":2}";
		when(restTemplate.getForEntity(anyString(), eq(String.class)))
				.thenReturn(ResponseEntity.ok(json));

		var result = inventoryService.getInventory("AVAILABLE", null);

		assertThat(result).isNotNull();
		assertThat(result.get("available").asInt()).isEqualTo(10);
	}

	@Test
	void getInventory_withLowStock_addsParam() throws Exception {
		String json = "{\"available\":10,\"pending\":2}";
		when(restTemplate.getForEntity(anyString(), eq(String.class)))
				.thenReturn(ResponseEntity.ok(json));

		var result = inventoryService.getInventory(null, 5);

		assertThat(result).isNotNull();
	}

	@Test
	void getInventory_emptyBody_returnsEmptyNode() {
		when(restTemplate.getForEntity(anyString(), eq(String.class)))
				.thenReturn(ResponseEntity.ok(""));

		var result = inventoryService.getInventory(null, null);

		assertThat(result).isNotNull();
		assertThat(result.isEmpty()).isTrue();
	}

	@Test
	void getOrderLive_returnsOptionalWhenFound() throws Exception {
		String json = "{\"id\":1,\"petId\":\"1\",\"quantity\":1}";
		when(restTemplate.getForEntity(anyString(), eq(String.class)))
				.thenReturn(ResponseEntity.ok(json));

		Optional<com.fasterxml.jackson.databind.JsonNode> result = inventoryService.getOrderLive(1);

		assertThat(result).isPresent();
		assertThat(result.get().get("id").asInt()).isEqualTo(1);
	}

	@Test
	void getOrderLive_returnsEmpty_whenNotFound() {
		when(restTemplate.getForEntity(anyString(), eq(String.class)))
				.thenThrow(new org.springframework.web.client.HttpClientErrorException(
						org.springframework.http.HttpStatus.NOT_FOUND));

		Optional<com.fasterxml.jackson.databind.JsonNode> result = inventoryService.getOrderLive(999);

		assertThat(result).isEmpty();
	}

	@Test
	void refresh_returnsJson_whenSuccess() throws Exception {
		String json = "{\"available\":5}";
		when(restTemplate.postForEntity(anyString(), eq(null), eq(String.class)))
				.thenReturn(ResponseEntity.ok(json));

		var result = inventoryService.refresh();

		assertThat(result).isNotNull();
		assertThat(result.get("available").asInt()).isEqualTo(5);
	}

	@Test
	void refresh_returnsEmptyNode_whenException() {
		when(restTemplate.postForEntity(anyString(), eq(null), eq(String.class)))
				.thenThrow(new RuntimeException("Connection refused"));

		var result = inventoryService.refresh();

		assertThat(result).isNotNull();
		assertThat(result.isEmpty()).isTrue();
	}
}
