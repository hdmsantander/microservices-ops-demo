package mx.hdmsantander.opsdemo.inventory.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import io.github.resilience4j.core.functions.Either;
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
	void retry_inventoryService_has_5_attempts_and_exponential_backoff() {
		Retry retry = retryRegistry.retry("inventoryService");
		RetryConfig config = retry.getRetryConfig();

		assertThat(config.getMaxAttempts()).isEqualTo(5);
		var either = Either.<Throwable, Object>left(new RuntimeException("test"));
		assertThat(config.getIntervalBiFunction().apply(1, either)).isEqualTo(500L);
		assertThat(config.getIntervalBiFunction().apply(2, either)).isEqualTo(1000L);
	}

	@Test
	void retry_orderService_has_5_attempts_exponential_backoff_and_ignores_404() {
		Retry retry = retryRegistry.retry("orderService");
		RetryConfig config = retry.getRetryConfig();

		assertThat(config.getMaxAttempts()).isEqualTo(5);
		var either = Either.<Throwable, Object>left(new RuntimeException("test"));
		assertThat(config.getIntervalBiFunction().apply(1, either)).isEqualTo(500L);
		assertThat(config.getIntervalBiFunction().apply(2, either)).isEqualTo(1000L);
		// 404 NotFound should not trigger retry (predicate returns false)
		var notFound = org.springframework.web.client.HttpClientErrorException
				.create(org.springframework.http.HttpStatus.NOT_FOUND, "Not Found",
						org.springframework.http.HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
		assertThat(config.getExceptionPredicate().test(notFound)).isFalse();
	}
}
