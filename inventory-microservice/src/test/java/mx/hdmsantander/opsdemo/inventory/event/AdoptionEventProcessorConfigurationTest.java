package mx.hdmsantander.opsdemo.inventory.event;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class AdoptionEventProcessorConfigurationTest {

	@Mock
	StreamBridge streamBridge;

	AdoptionEventProcessorConfiguration config;

	@BeforeEach
	void setUp() {
		config = new AdoptionEventProcessorConfiguration();
		ReflectionTestUtils.setField(config, "streamBridge", streamBridge);
	}

	@Test
	void adoptionEventProcessor_sendsCongratulationEvent() {
		AdoptionEvent event = AdoptionEvent.builder()
				.petId("pet-1")
				.name("Fluffy")
				.dateOfAdoption("2025-02-26")
				.build();
		Message<AdoptionEvent> message = MessageBuilder.withPayload(event).build();

		config.adoptionEventProcessor().accept(message);

		AdoptionCongratulationEvent expected = AdoptionCongratulationEvent.builder().petName("Fluffy").build();
		verify(streamBridge).send(eq("adoptionCongratulationOut"), eq(expected));
	}
}
