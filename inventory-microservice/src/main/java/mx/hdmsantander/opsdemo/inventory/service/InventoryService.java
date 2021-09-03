package mx.hdmsantander.opsdemo.inventory.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InventoryService {

	private static final String INVENTORY_SERVICE_URL = "https://petstore.swagger.io/v2/store/inventory";

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private OrderService orderService;
	
	@Timed(value = "inventory.query.time", description = "Time taken to get the inventory from the pet shop API")
	@Retryable(include = ResourceAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
	public JsonNode getInventory() {

		log.info("Retrieving inventory from the inventory service of the pet shop at: " + INVENTORY_SERVICE_URL);

		ResponseEntity<JsonNode> responseEntity = restTemplate.getForEntity(INVENTORY_SERVICE_URL, JsonNode.class);

		log.info("The request got back the status: " + responseEntity.getStatusCode());

		JsonNode inventory = responseEntity.getBody();

		log.info("Request was successful! Returning inventory");

		return inventory;

	}

	@Scheduled(fixedDelay = 20000)
	public void refreshOrders() {

		log.info("Running scheduled update of the current orders");
		orderService.updateOrders();

	}

}
