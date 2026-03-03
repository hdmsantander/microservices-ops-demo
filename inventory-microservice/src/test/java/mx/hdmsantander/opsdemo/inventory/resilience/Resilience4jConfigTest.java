package mx.hdmsantander.opsdemo.inventory.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import mx.hdmsantander.opsdemo.inventory.InventoryApplication;

/**
 * Verifies that resilience4j configuration from application.yml is correctly
 * applied to circuit breakers and retries in the inventory microservice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = InventoryApplication.class)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@EmbeddedKafka(bootstrapServersProperty = "spring.cloud.stream.kafka.binder.brokers")
class Resilience4jConfigTest {

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Autowired
	private RetryRegistry retryRegistry;

	@Test
	void circuitBreaker_registries_exist() {
		assertThat(circuitBreakerRegistry).isNotNull();
		assertThat(circuitBreakerRegistry.getAllCircuitBreakers()).isNotEmpty();
	}

	@Test
	void circuitBreaker_inventoryService_has_expected_config() {
		CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("inventoryService");
		CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

		assertThat(config.getFailureRateThreshold()).isEqualTo(20f);
		assertThat(config.getSlowCallRateThreshold()).isEqualTo(100f);
		assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(60));
		assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(5);
		assertThat(config.getSlidingWindowSize()).isEqualTo(20);
		assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
	}

	@Test
	void circuitBreaker_orderService_has_expected_config() {
		CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("orderService");
		CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

		assertThat(config.getFailureRateThreshold()).isEqualTo(20f);
		assertThat(config.getSlowCallRateThreshold()).isEqualTo(100f);
		assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(60));
		assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(5);
		assertThat(config.getSlidingWindowSize()).isEqualTo(20);
		assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
	}

	@Test
	void retry_inventoryService_has_5_attempts() {
		Retry retry = retryRegistry.retry("inventoryService");
		RetryConfig config = retry.getRetryConfig();

		assertThat(config.getMaxAttempts()).isEqualTo(5);
		assertThat(config.getWaitDuration()).isEqualTo(Duration.ofMillis(500));
		assertThat(config.isEnableExponentialBackoff()).isTrue();
		assertThat(config.getExponentialBackoffMultiplier()).isEqualTo(2.0);
	}

	@Test
	void retry_orderService_has_5_attempts_and_ignores_404() {
		Retry retry = retryRegistry.retry("orderService");
		RetryConfig config = retry.getRetryConfig();

		assertThat(config.getMaxAttempts()).isEqualTo(5);
		assertThat(config.getWaitDuration()).isEqualTo(Duration.ofMillis(500));
		assertThat(config.isEnableExponentialBackoff()).isTrue();
		assertThat(config.getExponentialBackoffMultiplier()).isEqualTo(2.0);
		assertThat(config.getIgnoreExceptions())
				.as("orderService retry should ignore 404 Not Found to avoid retrying missing orders")
				.anyMatch(clazz -> clazz != null && clazz.getName().contains("NotFound"));
	}
}
