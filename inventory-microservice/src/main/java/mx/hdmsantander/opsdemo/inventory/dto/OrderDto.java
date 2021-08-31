package mx.hdmsantander.opsdemo.inventory.dto;

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

	private Integer petId;

	private Integer quantity;

	private String shipDate;

	private String status;

	private Boolean complete;

	public static OrderEvent createOrderEvent(OrderDto o) {
		return OrderEvent.builder().id(o.getId()).petId(o.getPetId()).quantity(o.getQuantity())
				.shipDate(o.getShipDate()).status(o.getStatus()).complete(o.getComplete()).build();
	}

	public static PetShopOrder createPetShopOrder(OrderDto o) {
		return PetShopOrder.builder().orderId(o.getId()).petId(o.getPetId()).quantity(o.getQuantity())
				.shipDate(o.getShipDate()).status(o.getStatus()).complete(o.getComplete()).build();
	}

}
