package mx.hdmsantander.opsdemo.inventory.grpc;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;
import mx.hdmsantander.opsdemo.inventory.service.OrderService;
import mx.hdmsantander.opsdemo.inventory.v1.GetInventoryRequest;
import mx.hdmsantander.opsdemo.inventory.v1.GetInventoryResponse;
import mx.hdmsantander.opsdemo.inventory.v1.GetOrderRequest;
import mx.hdmsantander.opsdemo.inventory.v1.GetOrderResponse;
import mx.hdmsantander.opsdemo.inventory.v1.InventoryServiceGrpc;
import mx.hdmsantander.opsdemo.inventory.v1.NotFound;
import mx.hdmsantander.opsdemo.inventory.v1.Order;
import mx.hdmsantander.opsdemo.inventory.v1.RefreshInventoryRequest;
import mx.hdmsantander.opsdemo.inventory.v1.RefreshInventoryResponse;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.stereotype.Service;

/**
 * gRPC server implementation for Query ↔ Inventory internal communication.
 * Exposes GetInventory, GetOrder, and RefreshInventory over gRPC.
 */
@Slf4j
@Service
@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	public void getInventory(GetInventoryRequest request, StreamObserver<GetInventoryResponse> responseObserver) {
		try {
			String status = request.getStatus() != null && !request.getStatus().isBlank() ? request.getStatus() : null;
			Integer lowStockThreshold = request.getLowStockThreshold() != 0 ? request.getLowStockThreshold() : null;

			var inventory = inventoryService.getInventory(status, lowStockThreshold);
			String json = inventory != null ? objectMapper.writeValueAsString(inventory) : "{}";

			responseObserver.onNext(GetInventoryResponse.newBuilder().setJsonBody(json).build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.warn("gRPC GetInventory failed: {}", e.getMessage());
			responseObserver.onError(io.grpc.Status.INTERNAL.withCause(e).asException());
		}
	}

	@Override
	public void getOrder(GetOrderRequest request, StreamObserver<GetOrderResponse> responseObserver) {
		try {
			var orderOpt = orderService.getOrderById(request.getOrderId());
			GetOrderResponse.Builder builder = GetOrderResponse.newBuilder();

			if (orderOpt.isPresent()) {
				OrderDto dto = orderOpt.get();
				Order order = Order.newBuilder()
						.setId(dto.getId() != null ? dto.getId() : 0)
						.setPetId(dto.getPetId() != null ? dto.getPetId() : "")
						.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 0)
						.setShipDate(dto.getShipDate() != null ? dto.getShipDate() : "")
						.setStatus(dto.getStatus() != null ? dto.getStatus() : "")
						.setComplete(Boolean.TRUE.equals(dto.getComplete()))
						.build();
				builder.setOrder(order);
			} else {
				builder.setNotFound(NotFound.newBuilder().setMessage("Order not found").build());
			}

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.warn("gRPC GetOrder failed: {}", e.getMessage());
			responseObserver.onError(io.grpc.Status.INTERNAL.withCause(e).asException());
		}
	}

	@Override
	public void refreshInventory(RefreshInventoryRequest request,
			StreamObserver<RefreshInventoryResponse> responseObserver) {
		try {
			var inventory = inventoryService.refreshInventoryAndOrders();
			String json = inventory != null ? objectMapper.writeValueAsString(inventory) : "{}";

			responseObserver.onNext(RefreshInventoryResponse.newBuilder().setJsonBody(json).build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.warn("gRPC RefreshInventory failed: {}", e.getMessage());
			responseObserver.onError(io.grpc.Status.INTERNAL.withCause(e).asException());
		}
	}
}
