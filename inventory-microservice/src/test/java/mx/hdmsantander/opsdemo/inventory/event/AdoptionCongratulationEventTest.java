package mx.hdmsantander.opsdemo.inventory.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdoptionCongratulationEventTest {

	@Test
	void from_mapsAdoptionEventToCongratulationEvent() {
		AdoptionEvent adoptionEvent = AdoptionEvent.builder()
				.petId("pet-1")
				.name("Fluffy")
				.dateOfAdoption("2025-02-26")
				.build();

		AdoptionCongratulationEvent result = AdoptionCongratulationEvent.from(adoptionEvent);

		assertThat(result.getPetName()).isEqualTo("Fluffy");
	}
}
