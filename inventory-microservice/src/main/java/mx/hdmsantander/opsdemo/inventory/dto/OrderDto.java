package mx.hdmsantander.opsdemo.inventory.dto;

import org.springframework.integration.support.MessageBuilder;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mx.hdmsantander.opsdemo.inventory.event.OrderEvent;
import mx.hdmsantander.opsdemo.inventory.model.PetShopOrder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {

	private Integer id;

	private String petId;

	private Integer quantity;

	private String shipDate;

	private String status;

	private Boolean complete;

	public static Message<OrderEvent> createOrderEvent(OrderDto o) {
		return MessageBuilder.withPayload(OrderEvent.builder().id(o.getId()).petId(o.getPetId()).quantity(o.getQuantity())
				.shipDate(o.getShipDate()).status(o.getStatus()).complete(o.getComplete()).build())
				.setHeader(KafkaHeaders.MESSAGE_KEY, o.getId().toString())
				.build();
	}

	public static PetShopOrder createPetShopOrder(OrderDto o) {
		return PetShopOrder.builder().orderId(o.getId()).petId(o.getPetId()).quantity(o.getQuantity())
				.shipDate(o.getShipDate()).status(o.getStatus()).complete(o.getComplete()).build();
	}

}
