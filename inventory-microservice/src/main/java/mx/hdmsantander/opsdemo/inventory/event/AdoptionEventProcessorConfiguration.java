package mx.hdmsantander.opsdemo.inventory.event;

import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AdoptionEventProcessorConfiguration {

	@Autowired
	StreamBridge streamBridge;

	@Bean
	public Consumer<Message<AdoptionEvent>> adoptionEventProcessor() {
		return message -> {
			AdoptionEvent adoptionEvent = message.getPayload();
			log.info("Received an adoption event! Sending adoption congratulation event for {}", adoptionEvent.getName());
			AdoptionCongratulationEvent congratulation = AdoptionCongratulationEvent.from(adoptionEvent);
			streamBridge.send("adoptionCongratulationOut", congratulation);
		};
	}
}
