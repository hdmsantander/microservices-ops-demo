package mx.hdmsantander.opsdemo.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mx.hdmsantander.opsdemo.inventory.model.PetShopOrder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

	private Integer id;

	private Integer petId;

	private Integer quantity;

	private String shipDate;

	private String status;

	private Boolean complete;

	public static PetShopOrder createPetShopOrder(OrderEvent o) {
		return PetShopOrder.builder().orderId(o.getId()).petId(o.getPetId()).quantity(o.getQuantity())
				.shipDate(o.getShipDate()).status(o.getStatus()).complete(o.getComplete()).build();
	}

}
