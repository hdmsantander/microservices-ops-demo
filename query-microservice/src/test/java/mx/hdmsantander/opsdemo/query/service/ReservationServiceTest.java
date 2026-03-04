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

	@Test
	void createReservation_throws_whenRedisFails() {
		when(valueOps.setIfAbsent(anyString(), anyString(), any()))
				.thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Redis down"));

		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
				() -> reservationService.createReservation("1"));
	}

	@Test
	void validateAndRelease_returnsTrue_whenValid() {
		String json = "{\"reservationId\":\"r-1\",\"createdAt\":\"2025-01-01T00:00:00Z\"}";
		when(valueOps.get("reservation:pet:1")).thenReturn(json);

		boolean result = reservationService.validateAndRelease("1", "r-1");

		assertThat(result).isTrue();
		verify(redisTemplate).delete("reservation:pet:1");
		verify(setOps).remove("reservations:active", "1");
	}

	@Test
	void validateAndRelease_returnsFalse_whenKeyMissing() {
		when(valueOps.get("reservation:pet:1")).thenReturn(null);

		boolean result = reservationService.validateAndRelease("1", "r-1");

		assertThat(result).isFalse();
	}

	@Test
	void validateAndRelease_returnsFalse_whenWrongToken() {
		String json = "{\"reservationId\":\"r-1\",\"createdAt\":\"2025-01-01T00:00:00Z\"}";
		when(valueOps.get("reservation:pet:1")).thenReturn(json);

		boolean result = reservationService.validateAndRelease("1", "wrong-token");

		assertThat(result).isFalse();
	}

	@Test
	void validateAndRelease_returnsFalse_whenInvalidJson() {
		when(valueOps.get("reservation:pet:1")).thenReturn("not-valid-json");

		boolean result = reservationService.validateAndRelease("1", "r-1");

		assertThat(result).isFalse();
	}

	@Test
	void getReservationStatus_returnsStatus_whenFound() {
		when(setOps.members("reservations:active")).thenReturn(java.util.Set.of("1"));
		String json = "{\"reservationId\":\"r-1\",\"createdAt\":\"2025-01-01T00:00:00Z\"}";
		when(valueOps.get("reservation:pet:1")).thenReturn(json);
		when(redisTemplate.getExpire("reservation:pet:1", java.util.concurrent.TimeUnit.SECONDS)).thenReturn(300L);

		Optional<mx.hdmsantander.opsdemo.query.model.dto.ReservationStatusResponse> result =
				reservationService.getReservationStatus("r-1");

		assertThat(result).isPresent();
		assertThat(result.get().getReservationId()).isEqualTo("r-1");
		assertThat(result.get().getPetId()).isEqualTo("1");
		assertThat(result.get().isValid()).isTrue();
	}

	@Test
	void getReservationStatus_returnsEmpty_whenNotFound() {
		when(setOps.members("reservations:active")).thenReturn(java.util.Set.of());

		Optional<mx.hdmsantander.opsdemo.query.model.dto.ReservationStatusResponse> result =
				reservationService.getReservationStatus("r-unknown");

		assertThat(result).isEmpty();
	}

	@Test
	void getActiveReservationCount_returnsSize() {
		when(setOps.size("reservations:active")).thenReturn(5L);

		long count = reservationService.getActiveReservationCount();

		assertThat(count).isEqualTo(5);
	}

	@Test
	void getActiveReservationCount_returnsZero_whenException() {
		when(setOps.size("reservations:active"))
				.thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Redis down"));

		long count = reservationService.getActiveReservationCount();

		assertThat(count).isEqualTo(0);
	}

	@Test
	void isRedisAvailable_returnsTrue_whenPingSucceeds() {
		org.springframework.data.redis.connection.RedisConnection conn =
				org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnection.class);
		org.springframework.data.redis.connection.RedisConnectionFactory factory =
				org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
		when(redisTemplate.getConnectionFactory()).thenReturn(factory);
		when(factory.getConnection()).thenReturn(conn);

		boolean result = reservationService.isRedisAvailable();

		assertThat(result).isTrue();
	}

	@Test
	void isRedisAvailable_returnsFalse_whenPingFails() {
		org.springframework.data.redis.connection.RedisConnectionFactory factory =
				org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
		when(redisTemplate.getConnectionFactory()).thenReturn(factory);
		when(factory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

		boolean result = reservationService.isRedisAvailable();

		assertThat(result).isFalse();
	}

	@Test
	void cleanupExpiredFromSet_removesExpiredKeys() {
		when(setOps.members("reservations:active")).thenReturn(java.util.Set.of("1", "2"));
		when(redisTemplate.hasKey("reservation:pet:1")).thenReturn(false);
		when(redisTemplate.hasKey("reservation:pet:2")).thenReturn(true);

		int removed = reservationService.cleanupExpiredFromSet();

		assertThat(removed).isEqualTo(1);
		verify(setOps).remove("reservations:active", "1");
	}

	@Test
	void cleanupExpiredFromSet_returnsZero_whenEmpty() {
		when(setOps.members("reservations:active")).thenReturn(java.util.Set.of());

		int removed = reservationService.cleanupExpiredFromSet();

		assertThat(removed).isEqualTo(0);
	}
}
