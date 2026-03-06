package mx.hdmsantander.opsdemo.query.grpc;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.v1.GetInventoryRequest;
import mx.hdmsantander.opsdemo.inventory.v1.GetInventoryResponse;
import mx.hdmsantander.opsdemo.inventory.v1.GetOrderRequest;
import mx.hdmsantander.opsdemo.inventory.v1.GetOrderResponse;
import mx.hdmsantander.opsdemo.inventory.v1.InventoryServiceGrpc;
import mx.hdmsantander.opsdemo.inventory.v1.RefreshInventoryRequest;
import mx.hdmsantander.opsdemo.inventory.v1.RefreshInventoryResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * gRPC client for Inventory service. Used when inventory.grpc.enabled=true.
 * Provides GetInventory, GetOrderLive, and Refresh via gRPC.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "inventory.grpc.enabled", havingValue = "true")
public class InventoryGrpcClient {

	@GrpcClient("inventory")
	private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

	@Autowired
	private ObjectMapper objectMapper;

	public JsonNode getInventory(String status, Integer lowStockThreshold) {
		GetInventoryRequest.Builder req = GetInventoryRequest.newBuilder();
		if (status != null && !status.isBlank()) {
			req.setStatus(status);
		}
		if (lowStockThreshold != null) {
			req.setLowStockThreshold(lowStockThreshold);
		}
		GetInventoryResponse resp = inventoryStub.getInventory(req.build());
		String json = resp.getJsonBody();
		try {
			JsonNode node = objectMapper.readTree(json != null && !json.isEmpty() ? json : "{}");
			return node != null && !node.isMissingNode() ? node : objectMapper.createObjectNode();
		} catch (Exception e) {
			log.warn("Failed to parse inventory JSON from gRPC: {}", e.getMessage());
			return objectMapper.createObjectNode();
		}
	}

	public Optional<JsonNode> getOrderLive(Integer orderId) {
		GetOrderRequest req = GetOrderRequest.newBuilder().setOrderId(orderId).build();
		GetOrderResponse resp = inventoryStub.getOrder(req);
		if (resp.hasOrder()) {
			try {
				// Convert Order proto to JSON for compatibility with controller
				var order = resp.getOrder();
				String json = String.format(
						"{\"id\":%d,\"petId\":\"%s\",\"quantity\":%d,\"shipDate\":\"%s\",\"status\":\"%s\",\"complete\":%s}",
						order.getId(), order.getPetId(), order.getQuantity(),
						order.getShipDate(), order.getStatus(), order.getComplete());
				return Optional.of(objectMapper.readTree(json));
			} catch (Exception e) {
				log.warn("Failed to serialize order from gRPC: {}", e.getMessage());
			}
		}
		return Optional.empty();
	}

	public JsonNode refresh() {
		RefreshInventoryRequest req = RefreshInventoryRequest.newBuilder().build();
		RefreshInventoryResponse resp = inventoryStub.refreshInventory(req);
		String json = resp.getJsonBody();
		try {
			JsonNode node = objectMapper.readTree(json != null && !json.isEmpty() ? json : "{}");
			return node != null && !node.isMissingNode() ? node : objectMapper.createObjectNode();
		} catch (Exception e) {
			log.warn("Failed to parse refresh JSON from gRPC: {}", e.getMessage());
			return objectMapper.createObjectNode();
		}
	}
}
