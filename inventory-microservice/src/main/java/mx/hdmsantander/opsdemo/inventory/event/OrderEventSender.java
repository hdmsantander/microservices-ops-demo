package mx.hdmsantander.opsdemo.inventory.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;

@Slf4j
@Service
@AllArgsConstructor
public class OrderEventSender {

	@Autowired
	private StreamBridge steamBridge;

	public void send(OrderDto o) {
		log.info("Processing order DTO for order " + o.getId() + " updating order and sending order event!");
		steamBridge.send("orderEventSupplier-out-0", OrderDto.createOrderEvent(o));
	}

}
