package mx.hdmsantander.opsdemo.query.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;
import mx.hdmsantander.opsdemo.query.service.InventoryService;
import mx.hdmsantander.opsdemo.query.service.PetService;
import mx.hdmsantander.opsdemo.query.service.PetShopOrderService;

@ExtendWith(MockitoExtension.class)
class MainControllerTest {

	private MockMvc mockMvc;

	@Mock
	private PetService petService;

	@Mock
	private InventoryService inventoryService;

	@Mock
	private PetShopOrderService petShopOrderService;

	@BeforeEach
	void setUp() {
		MainController controller = new MainController();
		controller.petService = petService;
		controller.inventoryService = inventoryService;
		controller.petShopOrderService = petShopOrderService;
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void getInventory_returnsOk() throws Exception {
		JsonNode node = new ObjectMapper().createObjectNode();
		when(inventoryService.getInventory()).thenReturn(node);
		mockMvc.perform(get("/v1/inventory").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getPets_returnsOk() throws Exception {
		List<Pet> pets = Collections.emptyList();
		when(petService.getPetListByStatus(PetStatus.AVAILABLE)).thenReturn(pets);
		mockMvc.perform(get("/v1/pet").param("status", "AVAILABLE").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getOrders_returnsOk() throws Exception {
		List<PetShopOrder> orders = Collections.emptyList();
		when(petShopOrderService.getAllOrders()).thenReturn(orders);
		mockMvc.perform(get("/v1/orders").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void adoptPet_returnsOk() throws Exception {
		Pet pet = new Pet();
		pet.setId("1");
		pet.setName("TestPet");
		when(petService.adoptPetById("1")).thenReturn(pet);
		mockMvc.perform(post("/v1/pet/1/adopt").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
}
