package mx.hdmsantander.opsdemo.query.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;
import mx.hdmsantander.opsdemo.query.model.dto.AdoptionsStatsResponse;
import mx.hdmsantander.opsdemo.query.model.dto.ReservationResponse;
import mx.hdmsantander.opsdemo.query.model.dto.ReservationStatusResponse;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;
import mx.hdmsantander.opsdemo.query.service.InventoryService;
import mx.hdmsantander.opsdemo.query.service.PetService;
import mx.hdmsantander.opsdemo.query.service.PetShopOrderService;
import mx.hdmsantander.opsdemo.query.service.ReservationService;

@Slf4j
@RestController
@RequestMapping("/v1")
@Tag(name = "main", description = "Query microservice API")
@CrossOrigin(origins = "*")
public class MainController {

	private static final String RESERVATION_TOKEN_HEADER = "X-Reservation-Token";

	@Autowired
	private PetService petService;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private PetShopOrderService petShopOrderService;

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private MeterRegistry meterRegistry;

	@Operation(summary = "List or get pets", description = "List all pets, by status, by id, or by status with tags filter")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "404", description = "Pet not found (when id filter used)"),
			@ApiResponse(responseCode = "500", description = "Error while retrieving pets") })
	@GetMapping(path = "/pets", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getPets(
			@Parameter(description = "Pet ID for single pet fetch") @RequestParam(name = "id", required = false) String id,
			@Parameter(description = "Pet status filter", example = "AVAILABLE") @RequestParam(name = "status", required = false) PetStatus status,
			@Parameter(description = "Comma-separated tag names") @RequestParam(name = "tags", required = false) String tagsParam) {
		log.info("Controller /pets got a request, processing...");
		List<String> tags = tagsParam != null && !tagsParam.isBlank()
				? Arrays.asList(tagsParam.split(","))
				: null;
		Object result = petService.getPets(id, status, tags);
		if (id != null && !id.isBlank()) {
			if (result instanceof Pet pet && pet != null) {
				return ResponseEntity.ok(pet);
			}
			return ResponseEntity.notFound().build();
		}
		@SuppressWarnings("unchecked")
		List<Pet> pets = result != null ? (List<Pet>) result : List.of();
		return ResponseEntity.ok(pets);
	}

	@Operation(summary = "Reserve a pet for adoption")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Reservation created"),
			@ApiResponse(responseCode = "409", description = "Pet already reserved"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@PostMapping(path = "/pets/{id}/reserve", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ReservationResponse> reservePet(
			@Parameter(description = "Pet ID") @PathVariable String id) {
		Optional<ReservationService.ReservationResult> opt = reservationService.createReservation(id);
		return opt
				.map(r -> ResponseEntity.status(HttpStatus.CREATED).body(
						ReservationResponse.builder()
								.reservationId(r.reservationId())
								.petId(r.petId())
								.expiresAt(r.expiresAt())
								.build()))
				.orElse(ResponseEntity.status(HttpStatus.CONFLICT).build());
	}

	@Operation(summary = "Adopt a pet", description = "Requires valid X-Reservation-Token header when Redis is available")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful adoption"),
			@ApiResponse(responseCode = "400", description = "No or invalid token"),
			@ApiResponse(responseCode = "409", description = "Expired reservation"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@PostMapping(path = "/pets/{id}/adopt", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Pet> adoptPet(
			@Parameter(description = "Pet ID") @PathVariable String id,
			@Parameter(description = "Reservation token") @RequestHeader(name = RESERVATION_TOKEN_HEADER, required = false) String token) {
		Pet pet = petService.adoptPetById(id, token);
		return pet != null ? ResponseEntity.ok(pet) : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
	}

	@Operation(summary = "Release reservation")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Released"),
			@ApiResponse(responseCode = "404", description = "Invalid token") })
	@DeleteMapping(path = "/pets/{id}/reserve")
	public ResponseEntity<Void> releaseReservation(
			@PathVariable String id,
			@RequestHeader(name = RESERVATION_TOKEN_HEADER, required = true) String token) {
		boolean released = reservationService.validateAndRelease(id, token);
		return released ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@Operation(summary = "Check reservation status")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Reservation status"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@GetMapping(path = "/reservations/{reservationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ReservationStatusResponse> getReservation(
			@PathVariable String reservationId) {
		return reservationService.getReservationStatus(reservationId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Get orders from local DB")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@GetMapping(path = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<PetShopOrder>> getOrders() {
		log.info("Controller /orders got a request, returning orders...");
		return ResponseEntity.ok(petShopOrderService.getAllOrders());
	}

	@Operation(summary = "Get order by ID from local DB")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "404", description = "Order not found"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@GetMapping(path = "/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PetShopOrder> getOrder(
			@PathVariable Integer orderId) {
		return petShopOrderService.getOrderById(orderId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Get live order from PetStore via Inventory")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "404", description = "Order not found"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@GetMapping(path = "/orders/{orderId}/live", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getOrderLive(@PathVariable Integer orderId) {
		return inventoryService.getOrderLive(orderId)
				.map(node -> ResponseEntity.ok(node.toPrettyString()))
				.orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Check inventory", description = "Proxies to Inventory with optional filter params")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getInventory(
			@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "lowStockThreshold", required = false) Integer lowStockThreshold) {
		log.info("Controller /inventory got a request, querying the inventory service...");
		JsonNode inventory = inventoryService.getInventory(status, lowStockThreshold);
		if (inventory == null || inventory.isEmpty()) {
			log.warn("Inventory from inventory microservice returned empty; mapping to server error");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		return ResponseEntity.ok(inventory.toPrettyString());
	}

	@Operation(summary = "Adoption statistics")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation") })
	@GetMapping(path = "/adoptions/stats", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AdoptionsStatsResponse> getAdoptionsStats() {
		double total = 0.0;
		for (io.micrometer.core.instrument.Counter c : meterRegistry.find("pet.adoptions").counters()) {
			total = c.count();
			break;
		}
		return ResponseEntity.ok(AdoptionsStatsResponse.builder().totalAdoptions((long) total).build());
	}

	@Operation(summary = "Trigger inventory/order refresh")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Refresh triggered"),
			@ApiResponse(responseCode = "500", description = "Error") })
	@PostMapping(path = "/inventory/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> refreshInventory() {
		JsonNode result = inventoryService.refresh();
		return ResponseEntity.ok(result != null ? result.toPrettyString() : "{}");
	}
}
