package mx.hdmsantander.opsdemo.inventory.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;

@Slf4j
@RestController
@RequestMapping("/v1")
@Tag(name = "main", description = "Inventory microservice API")
@CrossOrigin(origins = "*")
public class MainController {

	@Autowired
	private InventoryService inventoryService;

	@Operation(summary = "Check inventory", description = "Retrieves inventory from the pet shop API at https://petstore.swagger.io/v2/store/inventory")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "500", description = "Error while retrieving inventory") })
	@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> getInventory() {
		log.info("Controller /inventory of the inventory microservice got a request, processing...");
		JsonNode inventory = inventoryService.getInventory();
		if (inventory == null || inventory.isEmpty()) {
			log.warn("Inventory from pet shop API returned empty; mapping to server error");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		return ResponseEntity.status(HttpStatus.OK).body(inventory);
	}
}
