package mx.hdmsantander.opsdemo.query.event;

import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

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

	public static Message<AdoptionEvent> createEventFromPet(Pet pet, String date) {
		
		return MessageBuilder
		.withPayload(AdoptionEvent.builder().petId(pet.getId()).name(pet.getName()).dateOfAdoption(date).build())
		.setHeader(KafkaHeaders.MESSAGE_KEY, pet.getId())
		.build();
		
	}

}
