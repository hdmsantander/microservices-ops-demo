package mx.hdmsantander.opsdemo.query.event;

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

import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;

@ExtendWith(MockitoExtension.class)
class AdoptionEventSenderTest {

	@Mock
	private StreamBridge streamBridge;

	private AdoptionEventSender sender;

	@BeforeEach
	void setUp() {
		sender = new AdoptionEventSender();
		ReflectionTestUtils.setField(sender, "streamBridge", streamBridge);
	}

	@Test
	void send_invokesStreamBridge() {
		Pet pet = Pet.builder().id("1").name("Fluffy").status(PetStatus.AVAILABLE).build();

		sender.send(pet);

		verify(streamBridge).send(eq("adoptionEventOut"), any(AdoptionEvent.class));
	}
}
