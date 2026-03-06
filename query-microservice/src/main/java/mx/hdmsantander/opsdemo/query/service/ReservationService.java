package mx.hdmsantander.opsdemo.query.service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.configuration.ReservationProperties;
import mx.hdmsantander.opsdemo.query.model.dto.ReservationStatusResponse;

@Slf4j
@Service
public class ReservationService {

	private static final String KEY_PREFIX = "reservation:pet:";
	private static final String SET_ACTIVE = "reservations:active";

	private final RedisTemplate<String, String> redisTemplate;
	private final ReservationProperties properties;
	private final MeterRegistry meterRegistry;
	private final ObjectMapper objectMapper;

	@Autowired
	public ReservationService(RedisTemplate<String, String> redisTemplate,
			ReservationProperties properties,
			MeterRegistry meterRegistry,
			ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	void registerGauge() {
		Gauge.builder("reservations.active", this, ReservationService::getActiveReservationCount)
				.description("Number of active reservations")
				.register(meterRegistry);
	}

	public Optional<ReservationResult> createReservation(String petId) {
		String key = KEY_PREFIX + petId;
		String reservationId = UUID.randomUUID().toString();
		Instant expiresAt = Instant.now().plusSeconds(properties.getTtlSeconds());
		String value = String.format("{\"reservationId\":\"%s\",\"createdAt\":\"%s\"}",
				reservationId, Instant.now().toString());

		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value,
					java.time.Duration.ofSeconds(properties.getTtlSeconds()));
			if (Boolean.TRUE.equals(ok)) {
				redisTemplate.opsForSet().add(SET_ACTIVE, petId);
				meterRegistry.counter("reservations.created").increment();
				sample.stop(Timer.builder("reservation.create.time")
						.description("Time to create reservation")
						.register(meterRegistry));
				return Optional.of(new ReservationResult(reservationId, petId, expiresAt.toString()));
			}
			meterRegistry.counter("reservations.conflict").increment();
			return Optional.empty();
		} catch (DataAccessException e) {
			log.warn("Redis unavailable during reservation create: {}", e.getMessage());
			meterRegistry.counter("reservations.redis_unavailable").increment();
			throw e;
		}
	}

	public boolean validateAndRelease(String petId, String token) {
		String key = KEY_PREFIX + petId;
		String value = redisTemplate.opsForValue().get(key);
		if (value == null) {
			meterRegistry.counter("reservations.expired").increment();
			return false;
		}
		try {
			ReservationData data = objectMapper.readValue(value, ReservationData.class);
			if (!data.reservationId.equals(token)) {
				return false;
			}
		} catch (Exception e) {
			log.warn("Failed to parse reservation data: {}", e.getMessage());
			return false;
		}

		Timer.Sample sample = Timer.start(meterRegistry);
		redisTemplate.delete(key);
		redisTemplate.opsForSet().remove(SET_ACTIVE, petId);
		meterRegistry.counter("reservations.released").increment();
		sample.stop(Timer.builder("reservation.release.time")
				.description("Time to release reservation")
				.register(meterRegistry));
		return true;
	}

	public Optional<ReservationStatusResponse> getReservationStatus(String reservationId) {
		// We'd need to store reservationId -> petId mapping to look up by reservationId.
		// For simplicity, this endpoint looks up by iterating - acceptable for demo low volume.
		// Alternative: store reservation:id -> petId as secondary index.
		java.util.Set<String> members = redisTemplate.opsForSet().members(SET_ACTIVE);
		if (members == null) {
			return Optional.empty();
		}
		for (String petId : members) {
			String key = KEY_PREFIX + petId;
			String value = redisTemplate.opsForValue().get(key);
			if (value != null) {
				try {
					ReservationData data = objectMapper.readValue(value, ReservationData.class);
					if (data.reservationId.equals(reservationId)) {
						Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
						String expiresAt = ttl != null && ttl > 0
								? Instant.now().plusSeconds(ttl).toString()
								: "expired";
						return Optional.of(ReservationStatusResponse.builder()
								.reservationId(reservationId)
								.petId(petId)
								.expiresAt(expiresAt)
								.valid(true)
								.build());
					}
				} catch (Exception e) {
					// skip
				}
			}
		}
		return Optional.empty();
	}

	public long getActiveReservationCount() {
		try {
			Long size = redisTemplate.opsForSet().size(SET_ACTIVE);
			return size != null ? size : 0;
		} catch (DataAccessException e) {
			return 0;
		}
	}

	public boolean isRedisAvailable() {
		try {
			redisTemplate.getConnectionFactory().getConnection().ping();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Removes expired entries from reservations:active set (keys expired via TTL are not auto-removed from set).
	 */
	public int cleanupExpiredFromSet() {
		Set<String> members = redisTemplate.opsForSet().members(SET_ACTIVE);
		if (members == null || members.isEmpty()) {
			return 0;
		}
		int removed = 0;
		for (String petId : members) {
			String key = KEY_PREFIX + petId;
			if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
				redisTemplate.opsForSet().remove(SET_ACTIVE, petId);
				removed++;
			}
		}
		if (removed > 0) {
			log.debug("Cleaned up {} expired reservation entries from set", removed);
		}
		return removed;
	}

	public record ReservationResult(String reservationId, String petId, String expiresAt) {
	}

	private static class ReservationData {
		public String reservationId;
		public String createdAt;
	}
}
