package mx.hdmsantander.opsdemo.inventory.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.stub.StreamObserver;
import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;
import mx.hdmsantander.opsdemo.inventory.service.OrderService;
import mx.hdmsantander.opsdemo.inventory.v1.GetInventoryRequest;
import mx.hdmsantander.opsdemo.inventory.v1.GetInventoryResponse;
import mx.hdmsantander.opsdemo.inventory.v1.GetOrderRequest;
import mx.hdmsantander.opsdemo.inventory.v1.GetOrderResponse;
import mx.hdmsantander.opsdemo.inventory.v1.RefreshInventoryRequest;
import mx.hdmsantander.opsdemo.inventory.v1.RefreshInventoryResponse;

@ExtendWith(MockitoExtension.class)
class InventoryGrpcServiceTest {

	@Mock
	private InventoryService inventoryService;

	@Mock
	private OrderService orderService;

	@Mock
	private StreamObserver<GetInventoryResponse> inventoryObserver;

	@Mock
	private StreamObserver<GetOrderResponse> orderObserver;

	@Mock
	private StreamObserver<RefreshInventoryResponse> refreshObserver;

	private InventoryGrpcService grpcService;
	private ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		grpcService = new InventoryGrpcService();
		org.springframework.test.util.ReflectionTestUtils.setField(grpcService, "inventoryService", inventoryService);
		org.springframework.test.util.ReflectionTestUtils.setField(grpcService, "orderService", orderService);
		org.springframework.test.util.ReflectionTestUtils.setField(grpcService, "objectMapper", objectMapper);
	}

	@Test
	void getInventory_returnsJsonBody() throws Exception {
		String json = "{\"available\":10,\"pending\":2}";
		when(inventoryService.getInventory(null, null))
				.thenReturn(objectMapper.readTree(json));

		grpcService.getInventory(GetInventoryRequest.newBuilder().build(), inventoryObserver);

		ArgumentCaptor<GetInventoryResponse> captor = ArgumentCaptor.forClass(GetInventoryResponse.class);
		verify(inventoryObserver).onNext(captor.capture());
		verify(inventoryObserver).onCompleted();
		assertThat(captor.getValue().getJsonBody()).contains("\"available\":10");
	}

	@Test
	void getInventory_withFilters_passesToService() throws Exception {
		when(inventoryService.getInventory("available", 5))
				.thenReturn(objectMapper.createObjectNode().put("available", 3));

		grpcService.getInventory(
				GetInventoryRequest.newBuilder().setStatus("available").setLowStockThreshold(5).build(),
				inventoryObserver);

		verify(inventoryService).getInventory("available", 5);
		verify(inventoryObserver).onNext(any(GetInventoryResponse.class));
		verify(inventoryObserver).onCompleted();
	}

	@Test
	void getOrder_returnsOrder_whenFound() {
		OrderDto dto = OrderDto.builder().id(1).petId("p1").quantity(2).status("placed").complete(false).build();
		when(orderService.getOrderById(1)).thenReturn(Optional.of(dto));

		grpcService.getOrder(GetOrderRequest.newBuilder().setOrderId(1).build(), orderObserver);

		ArgumentCaptor<GetOrderResponse> captor = ArgumentCaptor.forClass(GetOrderResponse.class);
		verify(orderObserver).onNext(captor.capture());
		verify(orderObserver).onCompleted();
		assertThat(captor.getValue().hasOrder()).isTrue();
		assertThat(captor.getValue().getOrder().getId()).isEqualTo(1);
		assertThat(captor.getValue().getOrder().getPetId()).isEqualTo("p1");
	}

	@Test
	void getOrder_returnsNotFound_whenEmpty() {
		when(orderService.getOrderById(999)).thenReturn(Optional.empty());

		grpcService.getOrder(GetOrderRequest.newBuilder().setOrderId(999).build(), orderObserver);

		ArgumentCaptor<GetOrderResponse> captor = ArgumentCaptor.forClass(GetOrderResponse.class);
		verify(orderObserver).onNext(captor.capture());
		verify(orderObserver).onCompleted();
		assertThat(captor.getValue().hasNotFound()).isTrue();
		assertThat(captor.getValue().getNotFound().getMessage()).contains("not found");
	}

	@Test
	void refreshInventory_returnsJsonBody() throws Exception {
		String json = "{\"available\":5}";
		when(inventoryService.refreshInventoryAndOrders()).thenReturn(objectMapper.readTree(json));

		grpcService.refreshInventory(RefreshInventoryRequest.newBuilder().build(), refreshObserver);

		ArgumentCaptor<RefreshInventoryResponse> captor = ArgumentCaptor.forClass(RefreshInventoryResponse.class);
		verify(refreshObserver).onNext(captor.capture());
		verify(refreshObserver).onCompleted();
		assertThat(captor.getValue().getJsonBody()).contains("\"available\":5");
	}
}
