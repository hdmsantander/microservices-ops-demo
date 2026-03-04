package mx.hdmsantander.opsdemo.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mx.hdmsantander.opsdemo.query.configuration.ReservationProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {

	@Mock
	private RedisTemplate<String, String> redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	@Mock
	private SetOperations<String, String> setOps;

	private ReservationService reservationService;

	@BeforeEach
	void setUp() {
		ReservationProperties properties = new ReservationProperties();
		properties.setTtlSeconds(300);
		properties.setCleanupIntervalSeconds(60);
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(redisTemplate.opsForSet()).thenReturn(setOps);
		reservationService = new ReservationService(redisTemplate, properties, new SimpleMeterRegistry(), new ObjectMapper());
	}

	@Test
	void createReservation_returnsResult_whenSetSucceeds() {
		when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

		Optional<ReservationService.ReservationResult> result = reservationService.createReservation("1");

		assertThat(result).isPresent();
		assertThat(result.get().petId()).isEqualTo("1");
		assertThat(result.get().reservationId()).isNotBlank();
		verify(setOps).add("reservations:active", "1");
	}

	@Test
	void createReservation_returnsEmpty_whenAlreadyReserved() {
		when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

		Optional<ReservationService.ReservationResult> result = reservationService.createReservation("1");

		assertThat(result).isEmpty();
	}
}
