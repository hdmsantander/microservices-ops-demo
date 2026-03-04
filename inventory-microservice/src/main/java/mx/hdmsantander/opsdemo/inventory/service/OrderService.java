package mx.hdmsantander.opsdemo.inventory.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

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
		for (int i = 0; i < 3; i++) {
			int orderId = ThreadLocalRandom.current().nextInt(1, 11);
			try {
				self.fetchOrder(orderId);
			} catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
				log.debug("Order {} not found in petstore (expected for demo API)", orderId);
			} catch (Exception e) {
				log.warn("Unexpected error updating order with ID {}: {}", orderId, e.getMessage());
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

		log.info("The order request got back the status: " + responseEntity.getStatusCode());

		if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
			log.info("Request was successful for order ID: {}! Emitting event to update orders!", orderId);
			meterRegistry.counter("orders.updated").increment();
			orderEventService.send(responseEntity.getBody());
			return responseEntity.getBody();
		}
		return null;
	}

	@SuppressWarnings("unused")
	private OrderDto fetchOrderFallback(int orderId, Exception e) {
		log.warn("Circuit breaker fallback for fetchOrder {}: {}", orderId, e.getMessage());
		return null;
	}

	@CircuitBreaker(name = "orderService", fallbackMethod = "getOrderByIdFallback")
	@Retry(name = "orderService")
	@Timed(value = "orders.get.time", description = "Time to get order by ID from PetStore")
	public Optional<OrderDto> getOrderById(int orderId) {
		meterRegistry.counter("orders.queries").increment();
		OrderDto dto = fetchOrder(orderId);
		return Optional.ofNullable(dto);
	}

	@SuppressWarnings("unused")
	private Optional<OrderDto> getOrderByIdFallback(int orderId, Exception e) {
		log.warn("Circuit breaker fallback for getOrderById {}: {}", orderId, e.getMessage());
		return Optional.empty();
	}
}
