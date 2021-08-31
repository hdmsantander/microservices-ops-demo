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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;

@Slf4j
@RestController
@RequestMapping("/v1")
@Api(value = "main")
@CrossOrigin(origins = "*")
public class MainController {

	@Autowired
	private InventoryService inventoryService;

	@ApiOperation(value = "Check availability of the inventory", notes = "Retrieves a list of the items in the inventory from the API of the pet shop at: https://petstore.swagger.io/v2/store/inventory")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation", response = JsonNode.class),
			@ApiResponse(code = 400, message = "Bad request"),
			@ApiResponse(code = 500, message = "Error while retrieving inventory") })
	@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> getInventory() {

		log.info("Controller /inventory of the inventory microservice got a request, processing...");

		JsonNode inventory = inventoryService.getInventory();
		return ResponseEntity.status(HttpStatus.OK).body(inventory);

	}

}
