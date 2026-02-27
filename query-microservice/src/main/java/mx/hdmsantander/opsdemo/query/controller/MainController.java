package mx.hdmsantander.opsdemo.query.controller;

import java.util.List;

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
import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;
import mx.hdmsantander.opsdemo.query.service.InventoryService;
import mx.hdmsantander.opsdemo.query.service.PetService;
import mx.hdmsantander.opsdemo.query.service.PetShopOrderService;

@Slf4j
@RestController
@RequestMapping("/v1")
@Tag(name = "main", description = "Query microservice API")
@CrossOrigin(origins = "*")
public class MainController {

	@Autowired
	private PetService petService;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private PetShopOrderService petShopOrderService;

	@Operation(summary = "Retrieve a list of pets", description = "Retrieves a list of pets with a certain status from the pet shop API at https://petstore.swagger.io")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "500", description = "Error while retrieving pets") })
	@GetMapping(path = "/pet", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Pet>> getPets(
			@Parameter(description = "Pet status", required = true, example = "AVAILABLE") @RequestParam(name = "status", required = true) PetStatus status) {
		log.info("Controller /pet got a request, processing...");
		List<Pet> pets = petService.getPetListByStatus(status);
		return ResponseEntity.status(HttpStatus.OK).body(pets);
	}

	@Operation(summary = "Adopt a pet", description = "Adopts a pet from the pet shop API and sends an adoption event to the inventory microservice")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "500", description = "Error while adopting pet") })
	@PostMapping(path = "/pet/{id}/adopt", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Pet> retrieve(
			@Parameter(description = "Pet ID", required = true) @PathVariable(name = "id", required = true) String id) {
		Pet pet = petService.adoptPetById(id);
		return ResponseEntity.status(HttpStatus.OK).body(pet);
	}

	@Operation(summary = "Check inventory", description = "Retrieves inventory from the inventory microservice at http://localhost:8081/v1/inventory")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "500", description = "Error while retrieving inventory") })
	@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> getInventory() {
		log.info("Controller /inventory got a request, querying the inventory service...");
		JsonNode inventory = inventoryService.getInventory();
		if (inventory == null || inventory.isEmpty()) {
			log.warn("Inventory from inventory microservice returned empty; mapping to server error");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		return ResponseEntity.status(HttpStatus.OK).body(inventory);
	}

	@Operation(summary = "Get orders", description = "Retrieves a list of orders received through events from the inventory microservice")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "500", description = "Error while retrieving orders") })
	@GetMapping(path = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<PetShopOrder>> getOrders() {
		log.info("Controller /orders got a request, returning orders...");
		return ResponseEntity.status(HttpStatus.OK).body(petShopOrderService.getAllOrders());
	}
}
