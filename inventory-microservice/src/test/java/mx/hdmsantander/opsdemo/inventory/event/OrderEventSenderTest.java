package mx.hdmsantander.opsdemo.inventory.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.util.ReflectionTestUtils;

import mx.hdmsantander.opsdemo.inventory.dto.OrderDto;

@ExtendWith(MockitoExtension.class)
class OrderEventSenderTest {

	@Mock
	private StreamBridge streamBridge;

	private OrderEventSender sender;

	@BeforeEach
	void setUp() {
		sender = new OrderEventSender();
		ReflectionTestUtils.setField(sender, "streamBridge", streamBridge);
	}

	@Test
	void send_invokesStreamBridge() {
		OrderDto dto = OrderDto.builder().id(1).petId("1").quantity(2).status("placed").complete(false).build();

		sender.send(dto);

		verify(streamBridge).send(eq("orderEventOut"), any(OrderEvent.class));
	}
}
