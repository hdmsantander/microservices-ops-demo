package mx.hdmsantander.opsdemo.inventory.event;

import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

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
		
	public static Message<AdoptionCongratulationEvent> createFromAdoptionEvent(AdoptionEvent a) {
		
		return MessageBuilder.withPayload(AdoptionCongratulationEvent.builder().petName(a.getName()).build())
		.setHeader(KafkaHeaders.MESSAGE_KEY, a.getPetId())
		.build();
		
	}

}
