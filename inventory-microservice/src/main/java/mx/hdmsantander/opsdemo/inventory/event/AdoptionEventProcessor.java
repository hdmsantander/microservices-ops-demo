package mx.hdmsantander.opsdemo.inventory.event;

import java.util.function.Function;

import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("adoptionEventProcessor")
@AllArgsConstructor
public class AdoptionEventProcessor implements Function<AdoptionEvent, Message<AdoptionCongratulationEvent>>{
	
	@Override
	public Message<AdoptionCongratulationEvent> apply(AdoptionEvent a) {
		
		log.info("Received an adoption event! Sending adoption congratulation event for " + a.getName());
		return AdoptionCongratulationEvent.createFromAdoptionEvent(a);
		
	}
	
	

}
