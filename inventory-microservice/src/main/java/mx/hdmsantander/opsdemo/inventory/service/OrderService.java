package mx.hdmsantander.opsdemo.inventory.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

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

	@Timed(value = "orders.query.time", description = "Time taken to query the pet shop API to refresh orders")
	@Retryable(include = ResourceAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
	public void updateOrders() {

		log.info("Updating orders using the pet shop API at: " + ORDER_SERVICE_BASE_URL);

		for (int i = 0; i < 3; i++) {

			int order = ThreadLocalRandom.current().nextInt(1, 10);

			try {

				log.info("Retrieving the order with ID: " + order);

				Map<String, Integer> uriVariables = new HashMap<>();
				uriVariables.put("order", order);

				ResponseEntity<OrderDto> responseEntity = restTemplate
						.getForEntity(ORDER_SERVICE_BASE_URL + "/store/order/{order}", OrderDto.class, uriVariables);

				log.info("The request got back the status: " + responseEntity.getStatusCode());

				if (responseEntity.getStatusCode().is2xxSuccessful()) {

					log.info("Request was successful! Emitting event to update orders!");
					meterRegistry.counter("orders.updated").increment();
					orderEventService.send(responseEntity.getBody());

				}

			} catch (Exception e) {
				log.error("Error updating order with ID: " + order, e);
			}

		}
	}
}
