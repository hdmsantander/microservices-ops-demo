package mx.hdmsantander.opsdemo.inventory.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import mx.hdmsantander.opsdemo.inventory.service.InventoryService;

@WebMvcTest(MainController.class)
class MainControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private InventoryService inventoryService;

	@Test
	void getInventory_returnsOk() throws Exception {
		JsonNode node = new ObjectMapper().createObjectNode();
		when(inventoryService.getInventory()).thenReturn(node);
		mockMvc.perform(get("/v1/inventory").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
}
