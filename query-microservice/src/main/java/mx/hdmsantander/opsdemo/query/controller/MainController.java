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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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
@Api(value = "main")
@CrossOrigin(origins = "*")
public class MainController {

	@Autowired
	private PetService petService;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private PetShopOrderService petShopOrderService;

	@ApiOperation(value = "Retrieve a list of pets from the pet shop API", notes = "Retrieves a list of pets with a certain status from the pet shop API at https://petstore.swagger.io")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Successful operation", response = Pet.class, responseContainer = "List"),
			@ApiResponse(code = 400, message = "Bad request"),
			@ApiResponse(code = 500, message = "Error while retrieving pets") })
	@GetMapping(path = "/pet", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Pet>> getPets(
			@ApiParam(name = "status", value = "status", required = true, example = "AVAILABLE") @RequestParam(name = "status", required = true) PetStatus status) {

		log.info("Controller /pet got a request, processing...");

		List<Pet> pets = petService.getPetListByStatus(status);
		return ResponseEntity.status(HttpStatus.OK).body(pets);

	}

	@ApiOperation(value = "Adopt a pet from the pet shop API acording to it's ID", notes = "Adopts a pet from the pet shop API at https://petstore.swagger.io and sends an adoption event to the inventory microservice")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation", response = Pet.class),
			@ApiResponse(code = 400, message = "Bad request"),
			@ApiResponse(code = 500, message = "Error while adopting pet") })
	@PostMapping(path = "/pet/{id}/adopt", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Pet> retrieve(
			@ApiParam(name = "id", value = "id", required = true, defaultValue = "0") @PathVariable(name = "id", required = true) String id) {

		Pet pet = petService.adoptPetById(id);
		return ResponseEntity.status(HttpStatus.OK).body(pet);

	}

	@ApiOperation(value = "Check availability of the inventory", notes = "Retrieves a list of the items in the inventory from the API of the inventory microservice at http://localhost:8079/v1/inventory")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation", response = JsonNode.class),
			@ApiResponse(code = 400, message = "Bad request"),
			@ApiResponse(code = 500, message = "Error while retrieving inventory") })
	@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> getInventory() {

		log.info("Controller /inventory got a request, querying the inventory service...");

		JsonNode inventory = inventoryService.getInventory();
		return ResponseEntity.status(HttpStatus.OK).body(inventory);

	}

	@ApiOperation(value = "Get orders", notes = "Retrieves a list of the orders received through events from the inventory microservice")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Successful operation", response = PetShopOrder.class, responseContainer = "List"),
			@ApiResponse(code = 400, message = "Bad request"),
			@ApiResponse(code = 500, message = "Error while retrieving orders") })
	@GetMapping(path = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<PetShopOrder>> getOrders() {

		log.info("Controller /orders got a request, returning orders...");

		return ResponseEntity.status(HttpStatus.OK).body(petShopOrderService.getAllOrders());

	}

}
