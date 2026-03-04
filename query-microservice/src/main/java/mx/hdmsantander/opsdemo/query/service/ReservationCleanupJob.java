package mx.hdmsantander.opsdemo.query.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import mx.hdmsantander.opsdemo.query.configuration.ReservationProperties;

@Slf4j
@Component
public class ReservationCleanupJob {

	private static final String KEY_PREFIX = "reservation:pet:";
	private static final String SET_ACTIVE = "reservations:active";

	private final ReservationService reservationService;
	private final ReservationProperties properties;
	private final MeterRegistry meterRegistry;

	@Autowired
	public ReservationCleanupJob(ReservationService reservationService,
			ReservationProperties properties,
			MeterRegistry meterRegistry) {
		this.reservationService = reservationService;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	@Scheduled(fixedDelayString = "${reservation.cleanup-interval-seconds:60}000")
	public void cleanupExpiredReservations() {
		if (!reservationService.isRedisAvailable()) {
			return;
		}
		Timer.Sample sample = Timer.start(meterRegistry);
		int removed = 0;
		try {
			// Use RedisTemplate from ReservationService - we need to access it
			// For simplicity, we'll add a cleanup method to ReservationService
			removed = reservationService.cleanupExpiredFromSet();
		} finally {
			if (removed > 0) {
				meterRegistry.counter("reservations.cleanup.removed").increment(removed);
			}
			sample.stop(Timer.builder("reservation.cleanup.time")
					.description("Time for reservation cleanup job")
					.register(meterRegistry));
		}
	}
}
