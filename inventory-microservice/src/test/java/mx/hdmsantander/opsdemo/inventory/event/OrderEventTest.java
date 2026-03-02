package mx.hdmsantander.opsdemo.inventory.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import mx.hdmsantander.opsdemo.inventory.model.PetShopOrder;

class OrderEventTest {

	@Test
	void createPetShopOrder_mapsEventToEntity() {
		OrderEvent event = OrderEvent.builder()
				.id(1)
				.petId("pet-1")
				.quantity(2)
				.shipDate("2025-01-01")
				.status("placed")
				.complete(true)
				.build();

		PetShopOrder order = OrderEvent.createPetShopOrder(event);

		assertThat(order.getOrderId()).isEqualTo(1);
		assertThat(order.getPetId()).isEqualTo("pet-1");
		assertThat(order.getQuantity()).isEqualTo(2);
		assertThat(order.getShipDate()).isEqualTo("2025-01-01");
		assertThat(order.getStatus()).isEqualTo("placed");
		assertThat(order.getComplete()).isTrue();
	}
}
