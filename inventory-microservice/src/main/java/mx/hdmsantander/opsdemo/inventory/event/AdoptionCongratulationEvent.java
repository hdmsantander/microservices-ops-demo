package mx.hdmsantander.opsdemo.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdoptionCongratulationEvent {

	private String petName;

	public static AdoptionCongratulationEvent from(AdoptionEvent a) {
		return AdoptionCongratulationEvent.builder().petName(a.getName()).build();
	}
}
