package mx.hdmsantander.opsdemo.query.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.hdmsantander.opsdemo.inventory.v1.GetInventoryResponse;
import mx.hdmsantander.opsdemo.inventory.v1.GetOrderResponse;
import mx.hdmsantander.opsdemo.inventory.v1.InventoryServiceGrpc;
import mx.hdmsantander.opsdemo.inventory.v1.Order;
import mx.hdmsantander.opsdemo.inventory.v1.RefreshInventoryResponse;

@ExtendWith(MockitoExtension.class)
class InventoryGrpcClientTest {

	@Mock
	private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

	private InventoryGrpcClient grpcClient;

	@BeforeEach
	void setUp() {
		grpcClient = new InventoryGrpcClient();
		org.springframework.test.util.ReflectionTestUtils.setField(grpcClient, "inventoryStub", inventoryStub);
		org.springframework.test.util.ReflectionTestUtils.setField(grpcClient, "objectMapper", new ObjectMapper());
	}

	@Test
	void getInventory_parsesJsonResponse() {
		when(inventoryStub.getInventory(any()))
				.thenReturn(GetInventoryResponse.newBuilder().setJsonBody("{\"available\":10}").build());

		var result = grpcClient.getInventory(null, null);

		assertThat(result).isNotNull();
		assertThat(result.get("available").asInt()).isEqualTo(10);
		verify(inventoryStub).getInventory(any());
	}

	@Test
	void getInventory_withParams_setsRequest() {
		when(inventoryStub.getInventory(any()))
				.thenReturn(GetInventoryResponse.newBuilder().setJsonBody("{}").build());

		grpcClient.getInventory("available", 5);

		verify(inventoryStub).getInventory(any());
	}

	@Test
	void getOrderLive_returnsEmpty_whenNotFound() {
		when(inventoryStub.getOrder(any()))
				.thenReturn(GetOrderResponse.newBuilder().build());

		var result = grpcClient.getOrderLive(999);

		assertThat(result).isEmpty();
	}

	@Test
	void getOrderLive_returnsOrder_whenFound() {
		Order order = Order.newBuilder()
				.setId(1).setPetId("p1").setQuantity(2)
				.setShipDate("").setStatus("placed").setComplete(false)
				.build();
		when(inventoryStub.getOrder(any()))
				.thenReturn(GetOrderResponse.newBuilder().setOrder(order).build());

		var result = grpcClient.getOrderLive(1);

		assertThat(result).isPresent();
		assertThat(result.get().get("id").asInt()).isEqualTo(1);
		assertThat(result.get().get("petId").asText()).isEqualTo("p1");
	}

	@Test
	void refresh_parsesJsonResponse() {
		when(inventoryStub.refreshInventory(any()))
				.thenReturn(RefreshInventoryResponse.newBuilder().setJsonBody("{\"available\":3}").build());

		var result = grpcClient.refresh();

		assertThat(result).isNotNull();
		assertThat(result.get("available").asInt()).isEqualTo(3);
		verify(inventoryStub).refreshInventory(any());
	}
}
