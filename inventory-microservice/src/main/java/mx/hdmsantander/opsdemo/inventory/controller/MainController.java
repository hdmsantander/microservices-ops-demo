package mx.hdmsantander.opsdemo.inventory.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;
import mx.hdmsantander.opsdemo.inventory.service.OrderService;

@Slf4j
@RestController
@RequestMapping("/v1")
@Tag(name = "main", description = "Inventory microservice API")
@CrossOrigin(origins = "*")
public class MainController {

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private OrderService orderService;

	@Operation(summary = "Check inventory", description = "Retrieves inventory from PetStore with optional status and lowStockThreshold filters")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "500", description = "Error while retrieving inventory") })
	@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getInventory(
			@Parameter(description = "Status key (available, pending, sold)") @RequestParam(name = "status", required = false) String status,
			@Parameter(description = "Keep only entries with value < threshold") @RequestParam(name = "lowStockThreshold", required = false) Integer lowStockThreshold) {
		log.info("Controller /inventory got a request, processing...");
		JsonNode inventory = inventoryService.getInventory(status, lowStockThreshold);
		if (inventory == null || inventory.isEmpty()) {
			log.warn("Inventory from pet shop API returned empty; mapping to server error");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		return ResponseEntity.ok(inventory.toPrettyString());
	}

	@Operation(summary = "Get order by ID from PetStore")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "404", description = "Order not found"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@GetMapping(path = "/order/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<OrderDto> getOrder(
			@Parameter(description = "Order ID") @PathVariable Integer orderId) {
		Optional<OrderDto> order = orderService.getOrderById(orderId);
		return order.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Trigger inventory/order refresh")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Refresh completed"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@PostMapping(path = "/inventory/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> refreshInventory() {
		log.info("Controller /inventory/refresh got a request");
		JsonNode inventory = inventoryService.refreshInventoryAndOrders();
		return ResponseEntity.ok(inventory);
	}
}
