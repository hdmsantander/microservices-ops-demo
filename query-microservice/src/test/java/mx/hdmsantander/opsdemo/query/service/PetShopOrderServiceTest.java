package mx.hdmsantander.opsdemo.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Sort;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mx.hdmsantander.opsdemo.query.data.PetShopOrderRepository;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

@ExtendWith(MockitoExtension.class)
class PetShopOrderServiceTest {

	@Mock
	PetShopOrderRepository petShopOrderRepository;

	PetShopOrderService petShopOrderService;

	@BeforeEach
	void setUp() {
		MeterRegistry registry = new SimpleMeterRegistry();
		petShopOrderService = new PetShopOrderService();
		ReflectionTestUtils.setField(petShopOrderService, "petShopOrderRepository", petShopOrderRepository);
		ReflectionTestUtils.setField(petShopOrderService, "meterRegistry", registry);
	}

	@Test
	void getAllOrders_returnsOrdersFromRepository() {
		PetShopOrder order = PetShopOrder.builder().orderId(1).petId("1").status("placed").build();
		when(petShopOrderRepository.findAll(any(Sort.class))).thenReturn(List.of(order));

		List<PetShopOrder> result = petShopOrderService.getAllOrders();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getOrderId()).isEqualTo(1);
		assertThat(result.get(0).getPetId()).isEqualTo("1");
	}

	@Test
	void getAllOrders_whenEmpty_returnsEmptyList() {
		when(petShopOrderRepository.findAll(any(Sort.class))).thenReturn(List.of());

		List<PetShopOrder> result = petShopOrderService.getAllOrders();

		assertThat(result).isEmpty();
	}
}
