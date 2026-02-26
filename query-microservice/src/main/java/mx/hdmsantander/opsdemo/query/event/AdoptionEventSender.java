package mx.hdmsantander.opsdemo.query.event;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.model.Pet;

@Slf4j
@Service
public class AdoptionEventSender {

	@Autowired
	StreamBridge streamBridge;

	public void send(Pet pet) {
		log.info("Sending adoption event for pet with the ID: {}", pet.getId());
		AdoptionEvent event = AdoptionEvent.createFromPet(pet, LocalDate.now().toString());
		streamBridge.send("adoptionEventOut", event);
	}
}
