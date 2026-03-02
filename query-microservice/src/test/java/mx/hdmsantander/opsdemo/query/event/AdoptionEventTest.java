package mx.hdmsantander.opsdemo.query.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import mx.hdmsantander.opsdemo.query.model.Pet;

class AdoptionEventTest {

	@Test
	void createFromPet_mapsPetToAdoptionEvent() {
		Pet pet = Pet.builder().id("pet-123").name("Fluffy").build();
		String date = "2025-02-26";

		AdoptionEvent event = AdoptionEvent.createFromPet(pet, date);

		assertThat(event.getPetId()).isEqualTo("pet-123");
		assertThat(event.getName()).isEqualTo("Fluffy");
		assertThat(event.getDateOfAdoption()).isEqualTo(date);
	}
}
