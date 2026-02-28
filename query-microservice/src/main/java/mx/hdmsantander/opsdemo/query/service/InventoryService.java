package mx.hdmsantander.opsdemo.query.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InventoryService {

	private static final String INVENTORY_SERVICE_URL = "http://localhost:8081/v1/inventory";

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Timed(value = "inventory.query.time", description = "Time taken to get the inventory from the inventory service")
	@Retryable(retryFor = ResourceAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
	public JsonNode getInventory() {
		log.info("Retrieving inventory from the inventory microservice at: " + INVENTORY_SERVICE_URL);
		try {
			ResponseEntity<String> responseEntity = restTemplate.getForEntity(INVENTORY_SERVICE_URL, String.class);
			log.info("The request got back the status: " + responseEntity.getStatusCode());
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
			return objectMapper.createObjectNode();
		}
	}

}
