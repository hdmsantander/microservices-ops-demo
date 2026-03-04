package mx.hdmsantander.opsdemo.query.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InventoryService {

	@Value("${inventory.service-url:http://localhost:8085}")
	private String inventoryBaseUrl;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
	@RateLimiter(name = "inventoryService")
	@Retry(name = "inventoryService")
	@Timed(value = "inventory.query.time", description = "Time taken to get the inventory from the inventory service")
	public JsonNode getInventory(String status, Integer lowStockThreshold) {
		String url = inventoryBaseUrl + "/v1/inventory";
		if (status != null || lowStockThreshold != null) {
			url += "?";
			if (status != null) {
				url += "status=" + status;
			}
			if (lowStockThreshold != null) {
				if (status != null) {
					url += "&";
				}
				url += "lowStockThreshold=" + lowStockThreshold;
			}
		}
		log.info("Retrieving inventory from the inventory microservice at: {}", url);
		try {
			ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
			log.info("The request got back the status: {}", responseEntity.getStatusCode());
			String body = responseEntity.getBody();
			if (body == null || body.isBlank()) {
				log.warn("Inventory microservice returned empty body");
				return objectMapper.createObjectNode();
			}
			JsonNode inventory = objectMapper.readTree(body);
			log.info("Request was successful! Returning inventory");
			return inventory != null && !inventory.isMissingNode() ? inventory : objectMapper.createObjectNode();
		} catch (Exception e) {
			log.warn("Failed to retrieve inventory from inventory microservice: {}", e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private JsonNode getInventoryFallback(String status, Integer lowStockThreshold, Exception e) {
		log.warn("Circuit breaker fallback for getInventory: {}", e.getMessage());
		return objectMapper.createObjectNode();
	}

	@CircuitBreaker(name = "inventoryService", fallbackMethod = "getOrderLiveFallback")
	@Retry(name = "inventoryService")
	@Timed(value = "orders.live.query.time", description = "Time to get live order from Inventory")
	public Optional<JsonNode> getOrderLive(Integer orderId) {
		String url = inventoryBaseUrl + "/v1/order/" + orderId;
		log.info("Fetching live order {} from Inventory at: {}", orderId, url);
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				return Optional.of(objectMapper.readTree(response.getBody()));
			}
		} catch (Exception e) {
			log.debug("Order {} not found from Inventory: {}", orderId, e.getMessage());
		}
		return Optional.empty();
	}

	private Optional<JsonNode> getOrderLiveFallback(Integer orderId, Exception e) {
		log.warn("Circuit breaker fallback for getOrderLive: {}", e.getMessage());
		return Optional.empty();
	}

	@CircuitBreaker(name = "inventoryService", fallbackMethod = "refreshFallback")
	@Retry(name = "inventoryService")
	public JsonNode refresh() {
		String url = inventoryBaseUrl + "/v1/inventory/refresh";
		log.info("Triggering inventory refresh at: {}", url);
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				return objectMapper.readTree(response.getBody());
			}
		} catch (Exception e) {
			log.warn("Failed to trigger inventory refresh: {}", e.getMessage());
		}
		return objectMapper.createObjectNode();
	}

	private JsonNode refreshFallback(Exception e) {
		log.warn("Circuit breaker fallback for refresh: {}", e.getMessage());
		return objectMapper.createObjectNode();
	}
}
