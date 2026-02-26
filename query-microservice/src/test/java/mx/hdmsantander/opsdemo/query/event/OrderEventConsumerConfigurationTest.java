package mx.hdmsantander.opsdemo.query.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import mx.hdmsantander.opsdemo.query.data.PetShopOrderRepository;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerConfigurationTest {

	@Mock
	PetShopOrderRepository petShopOrderRepository;

	OrderEventConsumerConfiguration config;

	@BeforeEach
	void setUp() {
		config = new OrderEventConsumerConfiguration();
		config.petShopOrderRepository = petShopOrderRepository;
	}

	@Test
	void orderEventConsumer_savesOrderToRepository() {
		OrderEvent event = OrderEvent.builder()
				.id(1)
				.petId("pet-1")
				.quantity(2)
				.shipDate("2025-01-01")
				.status("placed")
				.complete(true)
				.build();
		Message<OrderEvent> message = MessageBuilder.withPayload(event).build();

		config.orderEventConsumer().accept(message);

		verify(petShopOrderRepository).save(any(PetShopOrder.class));
	}
}
