package mx.hdmsantander.opsdemo.query.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InventoryService {

	private static final String INVENTORY_SERVICE_URL = "http://localhost:8079/v1/inventory";

	@Autowired
	private RestTemplate restTemplate;

	@Timed(value = "inventory.query.time", description = "Time taken to get the inventory from the inventory service")
	@Retryable(include = ResourceAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
	public JsonNode getInventory() {

		log.info("Retrieving inventory from the inventory microservice at: " + INVENTORY_SERVICE_URL);

		ResponseEntity<JsonNode> responseEntity = restTemplate.getForEntity(INVENTORY_SERVICE_URL, JsonNode.class);

		log.info("The request got back the status: " + responseEntity.getStatusCode());

		JsonNode inventory = responseEntity.getBody();

		log.info("Request was successful! Returning inventory");

		return inventory;

	}

}
