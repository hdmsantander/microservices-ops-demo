package mx.hdmsantander.opsdemo.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;
import mx.hdmsantander.opsdemo.inventory.event.OrderEventSender;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private OrderEventSender orderEventSender;

	private OrderService orderService;

	@BeforeEach
	void setUp() {
		orderService = new OrderService();
		ReflectionTestUtils.setField(orderService, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(orderService, "orderEventService", orderEventSender);
		ReflectionTestUtils.setField(orderService, "meterRegistry", new SimpleMeterRegistry());
		ReflectionTestUtils.setField(orderService, "self", orderService);
	}

	@Test
	void getOrderById_returnsOrder_whenFound() {
		OrderDto dto = OrderDto.builder().id(1).petId("1").quantity(2).status("placed").complete(false).build();
		when(restTemplate.getForEntity(any(String.class), eq(OrderDto.class), anyMap()))
				.thenReturn(ResponseEntity.ok(dto));

		Optional<OrderDto> result = orderService.getOrderById(1);

		assertThat(result).isPresent();
		assertThat(result.get().getId()).isEqualTo(1);
		assertThat(result.get().getPetId()).isEqualTo("1");
		verify(restTemplate).getForEntity(any(String.class), eq(OrderDto.class), anyMap());
	}

	@Test
	void getOrderById_returnsEmpty_whenApiReturnsNull() {
		when(restTemplate.getForEntity(any(String.class), eq(OrderDto.class), anyMap()))
				.thenReturn(ResponseEntity.ok().build());

		Optional<OrderDto> result = orderService.getOrderById(1);

		assertThat(result).isEmpty();
	}

	@Test
	void getOrderById_returnsEmpty_whenFetchReturnsNull() {
		when(restTemplate.getForEntity(any(String.class), eq(OrderDto.class), anyMap()))
				.thenReturn(ResponseEntity.ok().body(null));

		Optional<OrderDto> result = orderService.getOrderById(1);

		assertThat(result).isEmpty();
	}
}
