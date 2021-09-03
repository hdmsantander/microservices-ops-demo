package mx.hdmsantander.opsdemo.query.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mx.hdmsantander.opsdemo.query.model.Pet;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdoptionEvent {

	private String petId;

	private String name;

	private String dateOfAdoption;

	public static AdoptionEvent createEventFromPet(Pet pet, String date) {
		return AdoptionEvent.builder().petId(pet.getId()).name(pet.getName()).dateOfAdoption(date).build();
	}

}
