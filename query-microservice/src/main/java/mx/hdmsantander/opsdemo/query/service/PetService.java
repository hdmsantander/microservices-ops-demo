package mx.hdmsantander.opsdemo.query.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.event.AdoptionEventSender;
import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.Tag;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;

@Slf4j
@Service
public class PetService {

	private static final String PET_SHOP_BASE_URL = "https://petstore.swagger.io/v2";

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private AdoptionEventSender adoptionEventSender;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private ReservationService reservationService;

	/**
	 * Get pets by filters. When id is present, returns single pet (or null). Otherwise lists pets.
	 * When status is absent, lists all pets (calls findByStatus for each status and merges).
	 */
	@CircuitBreaker(name = "petService", fallbackMethod = "getPetsFallback")
	@Retry(name = "petService")
	@Timed(value = "pet.query.time", description = "Time taken to query and return pets")
	public Object getPets(String id, PetStatus status, List<String> tags) {
		if (id != null && !id.isBlank()) {
			meterRegistry.counter("pets.queried", "operation", "single").increment();
			Pet pet = getPetById(id);
			return pet;
		}

		List<Pet> pets;
		String operation;
		if (status != null) {
			pets = getPetListByStatus(status);
			operation = "list";
		} else {
			pets = getAllPets();
			operation = "all";
		}

		if (tags != null && !tags.isEmpty()) {
			pets = filterByTags(pets, tags);
			meterRegistry.counter("pets.queried", "operation", operation, "filter", "tags").increment();
		} else {
			meterRegistry.counter("pets.queried", "operation", operation).increment();
		}
		return pets;
	}

	private Object getPetsFallback(String id, PetStatus status, List<String> tags, Exception e) {
		log.warn("Circuit breaker fallback for getPets: {}", e.getMessage());
		if (id != null && !id.isBlank()) {
			return null;
		}
		return List.<Pet>of();
	}

	private Pet getPetById(String id) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("id", id);
		try {
			ResponseEntity<Pet> response = restTemplate.getForEntity(
					PET_SHOP_BASE_URL + "/pet/{id}", Pet.class, uriVariables);
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				return response.getBody();
			}
		} catch (Exception e) {
			log.debug("Pet {} not found: {}", id, e.getMessage());
		}
		return null;
	}

	private List<Pet> getPetListByStatus(PetStatus status) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("status", status.getCode());
		ResponseEntity<Pet[]> response = restTemplate.getForEntity(
				PET_SHOP_BASE_URL + "/pet/findByStatus?status={status}", Pet[].class, uriVariables);
		Pet[] arr = response.getBody();
		return arr != null ? Arrays.asList(arr) : List.of();
	}

	private List<Pet> getAllPets() {
		Map<String, Pet> byId = new LinkedHashMap<>();
		for (PetStatus s : PetStatus.values()) {
			try {
				List<Pet> list = getPetListByStatus(s);
				for (Pet p : list) {
					if (p != null && p.getId() != null) {
						byId.putIfAbsent(p.getId(), p);
					}
				}
			} catch (Exception e) {
				log.debug("Failed to fetch pets for status {}: {}", s, e.getMessage());
			}
		}
		return new ArrayList<>(byId.values());
	}

	private List<Pet> filterByTags(List<Pet> pets, List<String> tagNames) {
		return pets.stream()
				.filter(pet -> pet.getTags() != null
						&& tagNames.stream().allMatch(tagName -> pet.getTags().stream()
								.anyMatch(t -> t != null && tagName.equals(t.getName()))))
				.collect(Collectors.toList());
	}

	@CircuitBreaker(name = "petService", fallbackMethod = "adoptPetByIdFallback")
	@Retry(name = "petService")
	@Timed(value = "pet.adoption.time", description = "Time taken to adopt a pet")
	public Pet adoptPetById(String id, String reservationToken) {
		if (reservationToken != null && !reservationToken.isBlank()) {
			if (!reservationService.isRedisAvailable()) {
				log.warn("Redis unavailable; adopting without reservation validation");
				meterRegistry.counter("reservations.redis_unavailable").increment();
			} else if (!reservationService.validateAndRelease(id, reservationToken)) {
				log.warn("Invalid or expired reservation token for pet {}", id);
				return null;
			}
		} else if (reservationService.isRedisAvailable()) {
			log.warn("Adopt called without reservation token while Redis is available");
			return null;
		}

		log.info("Adopting pet with the ID " + id + ". validating if it exists");
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("id", id);
		ResponseEntity<Pet> responseEntity = restTemplate.getForEntity(
				PET_SHOP_BASE_URL + "/pet/{id}", Pet.class, uriVariables);
		if (!responseEntity.getStatusCode().is2xxSuccessful()) {
			return null;
		}
		Pet pet = responseEntity.getBody();
		if (pet == null) {
			return null;
		}
		log.info("The pet exists! Sending adoption event for " + pet.getName());
		meterRegistry.counter("pet.adoptions").increment();
		adoptionEventSender.send(pet);
		return pet;
	}

	private Pet adoptPetByIdFallback(String id, String reservationToken, Exception e) {
		log.warn("Circuit breaker fallback for adoptPetById: {}", e.getMessage());
		return null;
	}
}
