package mx.hdmsantander.opsdemo.query.event;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.model.Pet;

@Slf4j
@Service
@AllArgsConstructor
public class AdoptionEventSender {

	@Autowired
	private StreamBridge steamBridge;

	public void send(Pet pet) {
		log.info("Sending adoption event for pet with the ID: " + pet.getId());
		steamBridge.send("adoptionEventSupplier-out-0",
				AdoptionEvent.createEventFromPet(pet, LocalDate.now().toString()));
	}

}
