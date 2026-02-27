package mx.hdmsantander.opsdemo.inventory.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import mx.hdmsantander.opsdemo.inventory.service.InventoryService;

@ExtendWith(MockitoExtension.class)
class MainControllerTest {

	private MockMvc mockMvc;

	@Mock
	private InventoryService inventoryService;

	@BeforeEach
	void setUp() {
		MainController controller = new MainController();
		ReflectionTestUtils.setField(controller, "inventoryService", inventoryService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void getInventory_returnsOk() throws Exception {
		JsonNode node = new ObjectMapper().createObjectNode();
		when(inventoryService.getInventory()).thenReturn(node);
		mockMvc.perform(get("/v1/inventory").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
}
