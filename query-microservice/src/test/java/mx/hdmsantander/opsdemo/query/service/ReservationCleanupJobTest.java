package mx.hdmsantander.opsdemo.query.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mx.hdmsantander.opsdemo.query.configuration.ReservationProperties;

@ExtendWith(MockitoExtension.class)
class ReservationCleanupJobTest {

	@Mock
	private ReservationService reservationService;

	private ReservationCleanupJob job;

	@BeforeEach
	void setUp() {
		ReservationProperties props = new ReservationProperties();
		props.setCleanupIntervalSeconds(60);
		job = new ReservationCleanupJob(reservationService, props, new SimpleMeterRegistry());
	}

	@Test
	void cleanupExpiredReservations_skipsWhenRedisUnavailable() {
		when(reservationService.isRedisAvailable()).thenReturn(false);

		job.cleanupExpiredReservations();

		verify(reservationService, never()).cleanupExpiredFromSet();
	}

	@Test
	void cleanupExpiredReservations_invokesCleanupWhenRedisAvailable() {
		when(reservationService.isRedisAvailable()).thenReturn(true);
		when(reservationService.cleanupExpiredFromSet()).thenReturn(0);

		job.cleanupExpiredReservations();

		verify(reservationService).cleanupExpiredFromSet();
	}

	@Test
	void cleanupExpiredReservations_recordsMetricWhenRemovals() {
		when(reservationService.isRedisAvailable()).thenReturn(true);
		when(reservationService.cleanupExpiredFromSet()).thenReturn(3);

		job.cleanupExpiredReservations();

		verify(reservationService).cleanupExpiredFromSet();
	}
}
