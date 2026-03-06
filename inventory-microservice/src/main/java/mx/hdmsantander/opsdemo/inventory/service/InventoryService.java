package mx.hdmsantander.opsdemo.inventory.service;

import java.util.Iterator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InventoryService {

	private static final String INVENTORY_SERVICE_URL = "https://petstore.swagger.io/v2/store/inventory";

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OrderService orderService;

	@Autowired
	private Tracer tracer;

	@Autowired
	private MeterRegistry meterRegistry;

	@CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
	@Retry(name = "inventoryService")
	@Timed(value = "inventory.query.time", description = "Time taken to get the inventory from the pet shop API")
	public JsonNode getInventory(String status, Integer lowStockThreshold) {
		log.info("Retrieving inventory from the pet shop at: {}", INVENTORY_SERVICE_URL);
		try {
			ResponseEntity<String> responseEntity = restTemplate.getForEntity(INVENTORY_SERVICE_URL, String.class);
			log.info("The request got back the status: {}", responseEntity.getStatusCode());
			String body = responseEntity.getBody();
			if (body == null || body.isBlank()) {
				log.warn("Petstore API returned empty body");
				return objectMapper.createObjectNode();
			}
			JsonNode inventory = objectMapper.readTree(body);
			if (inventory == null || inventory.isMissingNode()) {
				inventory = objectMapper.createObjectNode();
			}

			// Apply filters per proposal: status first, then lowStockThreshold
			String filterTag = "raw";
			if (status != null && !status.isBlank()) {
				com.fasterxml.jackson.databind.node.ObjectNode filtered = objectMapper.createObjectNode();
				if (inventory.has(status)) {
					filtered.set(status, inventory.get(status));
				}
				inventory = filtered;
				filterTag = "status";
			}
			if (lowStockThreshold != null) {
				com.fasterxml.jackson.databind.node.ObjectNode filtered = objectMapper.createObjectNode();
				Iterator<String> it = inventory.fieldNames();
				while (it.hasNext()) {
					String key = it.next();
					JsonNode val = inventory.get(key);
					if (val != null && val.isNumber() && val.asInt() < lowStockThreshold) {
						filtered.set(key, val);
					}
				}
				inventory = filtered;
				filterTag = filterTag.equals("status") ? "both" : "lowStock";
				int count = 0;
				Iterator<String> fit = inventory.fieldNames();
				while (fit.hasNext()) {
					fit.next();
					count++;
				}
				meterRegistry.summary("inventory.low_stock.count", "filter", filterTag).record(count);
			}
			meterRegistry.counter("inventory.queries", "filter", filterTag).increment();
			log.info("Request was successful! Returning inventory");
			return inventory;
		} catch (Exception e) {
			log.warn("Failed to retrieve inventory from petstore API: {}", e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unused")
	private JsonNode getInventoryFallback(String status, Integer lowStockThreshold, Exception e) {
		log.warn("Circuit breaker fallback for getInventory: {}", e.getMessage());
		return objectMapper.createObjectNode();
	}

	@Scheduled(fixedDelay = 20000)
	public void refreshOrders() {
		Span span = tracer.nextSpan().name("refresh-orders").start();
		try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
			span.tag("scheduled", "true");
			log.info("Running scheduled update of the current orders");
			orderService.updateOrders();
		} finally {
			span.end();
		}
	}

	public JsonNode refreshInventoryAndOrders() {
		orderService.updateOrders();
		return getInventory(null, null);
	}
}
