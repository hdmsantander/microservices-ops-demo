package mx.hdmsantander.opsdemo.query.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.event.AdoptionEventSender;
import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;

@Slf4j
@Service
public class PetService {

	private static final String PET_SHOP_BASE_URL = "https://petstore.swagger.io/v2";

	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private AdoptionEventSender adoptionEventSender;

	@Retryable(include = ResourceAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
	public List<Pet> getPetListByStatus(PetStatus status) {

		log.info("Retrieving all pets with status " + status.getCode() + " from the pet shop at: " + PET_SHOP_BASE_URL);

		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("status", status.getCode());

		ResponseEntity<Pet[]> responseEntity = restTemplate
				.getForEntity(PET_SHOP_BASE_URL + "/pet/findByStatus?status={status}", Pet[].class, uriVariables);

		log.info("The request got back the status: " + responseEntity.getStatusCode());

		Pet[] petArray = responseEntity.getBody();

		log.info("Request was successful! Returning " + petArray.length + " pets");

		return Arrays.stream(petArray).collect(Collectors.toList());

	}
	
	public Pet adoptPetById(String id) {
		
		log.info("Adopting pet with the ID " + id + ". validating if it exists");
		
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("id",id);

		ResponseEntity<Pet> responseEntity = restTemplate
				.getForEntity(PET_SHOP_BASE_URL + "/pet/{id}", Pet.class, uriVariables);

		log.info("The request got back the status: " + responseEntity.getStatusCode());

		Pet pet = responseEntity.getBody();
		
		log.info("The pet exists! Sending adoption event for " + pet.getName());
		
		adoptionEventSender.send(pet);
		
		return pet;
		
	}

}
