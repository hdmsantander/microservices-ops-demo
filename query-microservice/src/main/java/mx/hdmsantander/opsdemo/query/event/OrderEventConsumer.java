package mx.hdmsantander.opsdemo.query.event;

import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.data.PetShopOrderRepository;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

@Slf4j
@Component("orderEventConsumer")
@AllArgsConstructor
public class OrderEventConsumer implements Consumer<Message<OrderEvent>> {

	@Autowired
	private PetShopOrderRepository petShopOrderRepository;

	@Override
	public void accept(Message<OrderEvent> orderEvent) {

		PetShopOrder order = OrderEvent.createPetShopOrder(orderEvent.getPayload());

		log.info("Received order event for order: " + order.getOrderId() + " updating!");

		petShopOrderRepository.save(order);
		
	}

}