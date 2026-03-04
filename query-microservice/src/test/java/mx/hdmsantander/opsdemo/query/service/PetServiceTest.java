package mx.hdmsantander.opsdemo.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mx.hdmsantander.opsdemo.query.event.AdoptionEventSender;
import mx.hdmsantander.opsdemo.query.model.Pet;
import mx.hdmsantander.opsdemo.query.model.Tag;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private AdoptionEventSender adoptionEventSender;

	@Mock
	private ReservationService reservationService;

	private PetService petService;

	@BeforeEach
	void setUp() {
		petService = new PetService();
		org.springframework.test.util.ReflectionTestUtils.setField(petService, "restTemplate", restTemplate);
		org.springframework.test.util.ReflectionTestUtils.setField(petService, "adoptionEventSender", adoptionEventSender);
		org.springframework.test.util.ReflectionTestUtils.setField(petService, "meterRegistry", new SimpleMeterRegistry());
		org.springframework.test.util.ReflectionTestUtils.setField(petService, "reservationService", reservationService);
	}

	@Test
	void getPets_singleId_returnsPet() {
		Pet pet = Pet.builder().id("1").name("Fluffy").status(PetStatus.AVAILABLE).build();
		when(restTemplate.getForEntity(anyString(), eq(Pet.class), any(Map.class)))
				.thenReturn(ResponseEntity.ok(pet));

		Object result = petService.getPets("1", null, null);

		assertThat(result).isInstanceOf(Pet.class);
		assertThat((Pet) result).extracting(Pet::getId, Pet::getName).containsExactly("1", "Fluffy");
		verify(restTemplate).getForEntity(anyString(), eq(Pet.class), any(Map.class));
	}

	@Test
	void getPets_singleId_notFound_returnsNull() {
		when(restTemplate.getForEntity(anyString(), eq(Pet.class), any(Map.class)))
				.thenThrow(new org.springframework.web.client.HttpClientErrorException(
						org.springframework.http.HttpStatus.NOT_FOUND));

		Object result = petService.getPets("999", null, null);

		assertThat(result).isNull();
	}

	@Test
	void getPets_byStatus_returnsList() {
		Pet[] pets = { Pet.builder().id("1").name("A").status(PetStatus.AVAILABLE).build() };
		when(restTemplate.getForEntity(anyString(), eq(Pet[].class), any(Map.class)))
				.thenReturn(ResponseEntity.ok(pets));

		Object result = petService.getPets(null, PetStatus.AVAILABLE, null);

		assertThat(result).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<Pet> list = (List<Pet>) result;
		assertThat(list).hasSize(1);
		assertThat(list.get(0).getName()).isEqualTo("A");
		verify(restTemplate).getForEntity(anyString(), eq(Pet[].class), any(Map.class));
	}

	@Test
	void getPets_allPets_mergesByStatus() {
		Pet[] av = { Pet.builder().id("1").name("A").status(PetStatus.AVAILABLE).build() };
		Pet[] pend = { Pet.builder().id("2").name("B").status(PetStatus.PENDING).build() };
		Pet[] sold = { Pet.builder().id("3").name("C").status(PetStatus.SOLD).build() };
		when(restTemplate.getForEntity(anyString(), eq(Pet[].class), any(Map.class)))
				.thenReturn(ResponseEntity.ok(av))
				.thenReturn(ResponseEntity.ok(pend))
				.thenReturn(ResponseEntity.ok(sold));

		Object result = petService.getPets(null, null, null);

		assertThat(result).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<Pet> list = (List<Pet>) result;
		assertThat(list).hasSize(3);
		verify(restTemplate, org.mockito.Mockito.atLeast(3)).getForEntity(anyString(), eq(Pet[].class), any(Map.class));
	}

	@Test
	void getPets_withTags_filtersPets() {
		Tag t1 = Tag.builder().name("fluffy").build();
		Pet p1 = Pet.builder().id("1").name("A").tags(List.of(t1)).build();
		Pet p2 = Pet.builder().id("2").name("B").tags(Collections.emptyList()).build();
		Pet[] av = { p1, p2 };
		when(restTemplate.getForEntity(anyString(), eq(Pet[].class), any(Map.class)))
				.thenReturn(ResponseEntity.ok(av));

		Object result = petService.getPets(null, PetStatus.AVAILABLE, List.of("fluffy"));

		assertThat(result).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<Pet> list = (List<Pet>) result;
		assertThat(list).hasSize(1);
		assertThat(list.get(0).getName()).isEqualTo("A");
	}

	@Test
	void adoptPetById_success_whenRedisUnavailable() {
		when(reservationService.isRedisAvailable()).thenReturn(false);
		Pet pet = Pet.builder().id("1").name("Fluffy").build();
		when(restTemplate.getForEntity(anyString(), eq(Pet.class), any(Map.class)))
				.thenReturn(ResponseEntity.ok(pet));

		Pet result = petService.adoptPetById("1", null);

		assertThat(result).isNotNull();
		assertThat(result.getName()).isEqualTo("Fluffy");
		verify(adoptionEventSender).send(pet);
	}

	@Test
	void adoptPetById_success_withValidToken() {
		when(reservationService.isRedisAvailable()).thenReturn(true);
		when(reservationService.validateAndRelease("1", "token-123")).thenReturn(true);
		Pet pet = Pet.builder().id("1").name("Fluffy").build();
		when(restTemplate.getForEntity(anyString(), eq(Pet.class), any(Map.class)))
				.thenReturn(ResponseEntity.ok(pet));

		Pet result = petService.adoptPetById("1", "token-123");

		assertThat(result).isNotNull();
		verify(reservationService).validateAndRelease("1", "token-123");
		verify(adoptionEventSender).send(pet);
	}

	@Test
	void adoptPetById_returnsNull_whenInvalidToken() {
		when(reservationService.isRedisAvailable()).thenReturn(true);
		when(reservationService.validateAndRelease("1", "bad-token")).thenReturn(false);

		Pet result = petService.adoptPetById("1", "bad-token");

		assertThat(result).isNull();
		verify(restTemplate, never()).getForEntity(anyString(), eq(Pet.class), any(Map.class));
	}

	@Test
	void adoptPetById_returnsNull_whenNoTokenAndRedisAvailable() {
		when(reservationService.isRedisAvailable()).thenReturn(true);

		Pet result = petService.adoptPetById("1", null);

		assertThat(result).isNull();
		verify(restTemplate, never()).getForEntity(anyString(), eq(Pet.class), any(Map.class));
	}

	@Test
	void adoptPetById_returnsNull_whenPetNotFound() {
		when(reservationService.isRedisAvailable()).thenReturn(false);
		when(restTemplate.getForEntity(anyString(), eq(Pet.class), any(Map.class)))
				.thenReturn(ResponseEntity.status(404).build());

		Pet result = petService.adoptPetById("999", null);

		assertThat(result).isNull();
	}
}
