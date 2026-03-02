package mx.hdmsantander.opsdemo.inventory.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;
import mx.hdmsantander.opsdemo.inventory.event.OrderEventSender;

@Slf4j
@Service
public class OrderService {

	private static final String ORDER_SERVICE_BASE_URL = "https://petstore.swagger.io/v2";

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private OrderEventSender orderEventService;

	@Autowired
	private MeterRegistry meterRegistry;

	@Lazy
	@Autowired
	private OrderService self;

	@Timed(value = "orders.query.time", description = "Time taken to query the pet shop API to refresh orders")
	public void updateOrders() {
		log.info("Updating orders using the pet shop API at: " + ORDER_SERVICE_BASE_URL);
		// Petstore accepts order IDs <= 5 or > 10; use 1-5 to avoid 404
		int[] orderIds = { 1, 2, 3, 4, 5 };

		for (int order : orderIds) {
			try {
				self.fetchOrder(order);
			} catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
				log.debug("Order {} not found in petstore (expected for demo API)", order);
			} catch (Exception e) {
				log.warn("Error updating order with ID {}: {}", order, e.getMessage());
			}
		}
	}

	@CircuitBreaker(name = "orderService", fallbackMethod = "fetchOrderFallback")
	@Retry(name = "orderService")
	OrderDto fetchOrder(int orderId) {
		log.info("Retrieving the order with ID: " + orderId);
		Map<String, Integer> uriVariables = new HashMap<>();
		uriVariables.put("order", orderId);

		ResponseEntity<OrderDto> responseEntity = restTemplate
				.getForEntity(ORDER_SERVICE_BASE_URL + "/store/order/{order}", OrderDto.class, uriVariables);

		log.info("The request got back the status: " + responseEntity.getStatusCode());

		if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
			log.info("Request was successful! Emitting event to update orders!");
			meterRegistry.counter("orders.updated").increment();
			orderEventService.send(responseEntity.getBody());
			return responseEntity.getBody();
		}
		return null;
	}

	private OrderDto fetchOrderFallback(int orderId, Exception e) {
		log.warn("Circuit breaker fallback for fetchOrder {}: {}", orderId, e.getMessage());
		return null;
	}
}
