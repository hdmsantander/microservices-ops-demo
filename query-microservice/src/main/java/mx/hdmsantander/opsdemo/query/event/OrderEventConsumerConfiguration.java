package mx.hdmsantander.opsdemo.query.event;

import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.data.PetShopOrderRepository;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

@Slf4j
@Configuration
public class OrderEventConsumerConfiguration {

	@Autowired
	PetShopOrderRepository petShopOrderRepository;

	@Bean
	public Consumer<Message<OrderEvent>> orderEventConsumer() {
		return message -> {
			OrderEvent orderEvent = message.getPayload();
			log.info("Received order event for order: {} updating!", orderEvent.getId());
			PetShopOrder order = OrderEvent.createPetShopOrder(orderEvent);
			petShopOrderRepository.save(order);
		};
	}
}
