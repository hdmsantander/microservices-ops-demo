package mx.hdmsantander.opsdemo.inventory.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;
import mx.hdmsantander.opsdemo.inventory.service.InventoryService;
import mx.hdmsantander.opsdemo.inventory.service.OrderService;

@ExtendWith(MockitoExtension.class)
class MainControllerTest {

	private MockMvc mockMvc;

	@Mock
	private InventoryService inventoryService;

	@Mock
	private OrderService orderService;

	@BeforeEach
	void setUp() {
		MainController controller = new MainController();
		ReflectionTestUtils.setField(controller, "inventoryService", inventoryService);
		ReflectionTestUtils.setField(controller, "orderService", orderService);
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
	void getOrder_returnsOk() throws Exception {
		OrderDto order = OrderDto.builder().id(1).petId("1").quantity(1).status("placed").build();
		when(orderService.getOrderById(1)).thenReturn(Optional.of(order));
		mockMvc.perform(get("/v1/order/1").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}

	@Test
	void getOrder_returns404_whenNotFound() throws Exception {
		when(orderService.getOrderById(999)).thenReturn(Optional.empty());
		mockMvc.perform(get("/v1/order/999").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void refreshInventory_returnsOk() throws Exception {
		JsonNode node = new ObjectMapper().createObjectNode().put("available", 5);
		when(inventoryService.refreshInventoryAndOrders()).thenReturn(node);
		mockMvc.perform(post("/v1/inventory/refresh").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
}
