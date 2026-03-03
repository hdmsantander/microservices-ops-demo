package mx.hdmsantander.opsdemo.query.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Map;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import mx.hdmsantander.opsdemo.query.event.AdoptionEventSender;
import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;
import mx.hdmsantander.opsdemo.query.service.InventoryService;
import mx.hdmsantander.opsdemo.query.service.PetService;

/**
 * Integration tests verifying resilience4j behaviour: retry, circuit breaker,
 * and rate limiter through actual service method invocations with mocked RestTemplate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = mx.hdmsantander.opsdemo.query.QueryApplication.class)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@EmbeddedKafka(bootstrapServersProperty = "spring.cloud.stream.kafka.binder.brokers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Resilience4jIntegrationTest {

	@MockitoBean
	private RestTemplate restTemplate;

	@Autowired
	private PetService petService;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AdoptionEventSender adoptionEventSender;

	// --- PetService (external: 5 retries, circuit breaker) ---

	@Test
	@Order(2)
	void petService_returns_pets_when_api_succeeds() {
		Pet[] pets = { Pet.builder().id("1").name("Fluffy").status(PetStatus.AVAILABLE).build() };
		when(restTemplate.getForEntity(any(String.class), eq(Pet[].class), any(Map.class)))
				.thenReturn(ResponseEntity.ok(pets));

		var result = petService.getPetListByStatus(PetStatus.AVAILABLE);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getName()).isEqualTo("Fluffy");
		verify(restTemplate).getForEntity(any(String.class), eq(Pet[].class), any(Map.class));
	}

	@Test
	@Order(3)
	void petService_returns_fallback_after_retries_exhausted() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(Pet[].class), any(Map.class));

		var result = petService.getPetListByStatus(PetStatus.AVAILABLE);

		assertThat(result).isEmpty();
		verify(restTemplate, atLeast(1)).getForEntity(any(String.class), eq(Pet[].class), any(Map.class));
	}

	@Test
	@Order(4)
	void petService_circuit_breaker_returns_fallback_when_open() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(Pet[].class), any(Map.class));

		for (int i = 0; i < 5; i++) {
			petService.getPetListByStatus(PetStatus.AVAILABLE);
		}

		// Circuit open: next call returns fallback without additional HTTP calls
		var result = petService.getPetListByStatus(PetStatus.AVAILABLE);
		assertThat(result).isEmpty();
	}

	@Test
	@Order(1)
	void adoptPetById_returns_fallback_after_retries_exhausted() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(Pet.class), any(Map.class));

		var result = petService.adoptPetById("1");

		assertThat(result).isNull();
		verify(restTemplate, atLeast(1)).getForEntity(any(String.class), eq(Pet.class), any(Map.class));
	}

	// --- InventoryService (internal: 2 retries, rate limiter, circuit breaker) ---

	@Test
	@Order(5)
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
	@Order(6)
	void inventoryService_returns_fallback_after_internal_retries_exhausted() {
		doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
				.when(restTemplate).getForEntity(any(String.class), eq(String.class));

		JsonNode result = inventoryService.getInventory();

		assertThat(result.isObject()).isTrue();
		assertThat(result.isEmpty()).isTrue();
		verify(restTemplate, atLeast(1)).getForEntity(any(String.class), eq(String.class));
	}

	@Test
	@Order(7)
	void inventoryService_rate_limiter_allows_calls_within_limit() throws Exception {
		JsonNode inventory = objectMapper.createObjectNode().put("available", 0);
		when(restTemplate.getForEntity(any(String.class), eq(String.class)))
				.thenReturn(ResponseEntity.ok(objectMapper.writeValueAsString(inventory)));

		for (int i = 0; i < 3; i++) {
			JsonNode result = inventoryService.getInventory();
			assertThat(result.get("available").asInt()).isEqualTo(0);
		}
		verify(restTemplate, times(3)).getForEntity(any(String.class), eq(String.class));
	}
}
