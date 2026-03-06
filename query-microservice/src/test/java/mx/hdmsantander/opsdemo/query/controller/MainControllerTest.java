package mx.hdmsantander.opsdemo.query.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;
import mx.hdmsantander.opsdemo.query.model.dto.ReservationStatusResponse;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;
import mx.hdmsantander.opsdemo.query.service.InventoryService;
import mx.hdmsantander.opsdemo.query.service.PetService;
import mx.hdmsantander.opsdemo.query.service.PetShopOrderService;
import mx.hdmsantander.opsdemo.query.service.ReservationService;

@ExtendWith(MockitoExtension.class)
class MainControllerTest {

	private MockMvc mockMvc;

	@Mock
	private PetService petService;

	@Mock
	private InventoryService inventoryService;

	@Mock
	private PetShopOrderService petShopOrderService;

	@Mock
	private ReservationService reservationService;

	@BeforeEach
	void setUp() {
		MainController controller = new MainController();
		ReflectionTestUtils.setField(controller, "petService", petService);
		ReflectionTestUtils.setField(controller, "inventoryService", inventoryService);
		ReflectionTestUtils.setField(controller, "petShopOrderService", petShopOrderService);
		ReflectionTestUtils.setField(controller, "reservationService", reservationService);
		ReflectionTestUtils.setField(controller, "meterRegistry", new SimpleMeterRegistry());
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void getInventory_returnsOk() throws Exception {
		JsonNode node = new ObjectMapper().createObjectNode().put("available", 0).put("pending", 0).put("sold", 0);
		when(inventoryService.getInventory(any(), any())).thenReturn(node);
		mockMvc.perform(get("/v1/inventory").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getInventory_returnsServerError_whenEmpty() throws Exception {
		when(inventoryService.getInventory(any(), any())).thenReturn(new ObjectMapper().createObjectNode());
		mockMvc.perform(get("/v1/inventory").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void getPets_returnsOk_withStatus() throws Exception {
		List<Pet> pets = Collections.emptyList();
		when(petService.getPets(eq(null), eq(PetStatus.AVAILABLE), eq(null))).thenReturn(pets);
		mockMvc.perform(get("/v1/pets").param("status", "AVAILABLE").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getPets_returnsOk_listAll() throws Exception {
		List<Pet> pets = Collections.emptyList();
		when(petService.getPets(eq(null), eq(null), eq(null))).thenReturn(pets);
		mockMvc.perform(get("/v1/pets").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getPets_returnsOk_singlePet() throws Exception {
		Pet pet = new Pet();
		pet.setId("1");
		pet.setName("TestPet");
		when(petService.getPets(eq("1"), any(), any())).thenReturn(pet);
		mockMvc.perform(get("/v1/pets").param("id", "1").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getPets_returns404_whenSinglePetNotFound() throws Exception {
		when(petService.getPets(eq("999"), any(), any())).thenReturn(null);
		mockMvc.perform(get("/v1/pets").param("id", "999").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void getOrders_returnsOk() throws Exception {
		List<PetShopOrder> orders = Collections.emptyList();
		when(petShopOrderService.getAllOrders()).thenReturn(orders);
		mockMvc.perform(get("/v1/orders").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getOrder_returnsOk() throws Exception {
		PetShopOrder order = PetShopOrder.builder().orderId(1).petId("1").quantity(1).build();
		when(petShopOrderService.getOrderById(1)).thenReturn(Optional.of(order));
		mockMvc.perform(get("/v1/orders/1").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getOrder_returns404_whenNotFound() throws Exception {
		when(petShopOrderService.getOrderById(999)).thenReturn(Optional.empty());
		mockMvc.perform(get("/v1/orders/999").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void adoptPet_returnsOk_withToken() throws Exception {
		Pet pet = new Pet();
		pet.setId("1");
		pet.setName("TestPet");
		when(petService.adoptPetById("1", "token-123")).thenReturn(pet);
		mockMvc.perform(post("/v1/pets/1/adopt")
				.header("X-Reservation-Token", "token-123")
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void adoptPet_returnsBadRequest_withoutValidPet() throws Exception {
		when(petService.adoptPetById("1", null)).thenReturn(null);
		mockMvc.perform(post("/v1/pets/1/adopt").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	@Test
	void reservePet_returns201() throws Exception {
		when(reservationService.createReservation("1"))
				.thenReturn(Optional.of(new ReservationService.ReservationResult("r-1", "1", "2025-12-31T00:00:00Z")));
		mockMvc.perform(post("/v1/pets/1/reserve").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated());
	}

	@Test
	void reservePet_returns409_whenConflict() throws Exception {
		when(reservationService.createReservation("1")).thenReturn(Optional.empty());
		mockMvc.perform(post("/v1/pets/1/reserve").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict());
	}

	@Test
	void releaseReservation_returns204() throws Exception {
		when(reservationService.validateAndRelease("1", "token-123")).thenReturn(true);
		mockMvc.perform(delete("/v1/pets/1/reserve").header("X-Reservation-Token", "token-123"))
				.andExpect(status().isNoContent());
	}

	@Test
	void getReservation_returns200() throws Exception {
		when(reservationService.getReservationStatus("r-1"))
				.thenReturn(Optional.of(ReservationStatusResponse.builder()
						.reservationId("r-1").petId("1").valid(true).build()));
		mockMvc.perform(get("/v1/reservations/r-1").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getAdoptionsStats_returns200() throws Exception {
		mockMvc.perform(get("/v1/adoptions/stats").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void refreshInventory_returns200() throws Exception {
		JsonNode node = new ObjectMapper().createObjectNode();
		when(inventoryService.refresh()).thenReturn(node);
		mockMvc.perform(post("/v1/inventory/refresh").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getOrderLive_returns200_whenFound() throws Exception {
		JsonNode node = new ObjectMapper().createObjectNode().put("id", 1).put("petId", "1");
		when(inventoryService.getOrderLive(1)).thenReturn(Optional.of(node));
		mockMvc.perform(get("/v1/orders/1/live").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getOrderLive_returns404_whenNotFound() throws Exception {
		when(inventoryService.getOrderLive(999)).thenReturn(Optional.empty());
		mockMvc.perform(get("/v1/orders/999/live").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void getReservation_returns404_whenNotFound() throws Exception {
		when(reservationService.getReservationStatus("r-unknown")).thenReturn(Optional.empty());
		mockMvc.perform(get("/v1/reservations/r-unknown").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void releaseReservation_returns404_whenInvalidToken() throws Exception {
		when(reservationService.validateAndRelease("1", "bad-token")).thenReturn(false);
		mockMvc.perform(delete("/v1/pets/1/reserve").header("X-Reservation-Token", "bad-token"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getPets_returnsOk_withTags() throws Exception {
		List<Pet> pets = Collections.emptyList();
		when(petService.getPets(eq(null), eq(PetStatus.AVAILABLE), any())).thenReturn(pets);
		mockMvc.perform(get("/v1/pets").param("status", "AVAILABLE").param("tags", "fluffy,dog")
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
}
