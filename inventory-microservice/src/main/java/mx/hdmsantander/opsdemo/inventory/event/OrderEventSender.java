package mx.hdmsantander.opsdemo.inventory.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;

@Slf4j
@Service
public class OrderEventSender {

	@Autowired
	private StreamBridge streamBridge;

	public void send(OrderDto dto) {
		log.info("Processing order DTO for order {} updating order and sending order event!", dto.getId());
		OrderEvent event = OrderDto.toOrderEvent(dto);
		streamBridge.send("orderEventOut", event);
	}
}
