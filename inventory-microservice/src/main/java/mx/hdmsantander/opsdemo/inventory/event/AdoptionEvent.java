package mx.hdmsantander.opsdemo.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdoptionEvent {
	
	private String petId;

	private String name;
	
	private String dateOfAdoption;
		
}
