package mx.hdmsantander.opsdemo.query.resilience;

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
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import mx.hdmsantander.opsdemo.query.QueryApplication;

/**
 * Verifies that resilience4j configuration from application.yml is correctly
 * applied to circuit breakers, retries, and rate limiters in the query microservice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = QueryApplication.class)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@EmbeddedKafka(bootstrapServersProperty = "spring.cloud.stream.kafka.binder.brokers")
class Resilience4jConfigTest {

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Autowired
	private RetryRegistry retryRegistry;

	@Autowired
	private RateLimiterRegistry rateLimiterRegistry;

	@Test
	void circuitBreaker_registries_exist() {
		assertThat(circuitBreakerRegistry).isNotNull();
		assertThat(circuitBreakerRegistry.getAllCircuitBreakers()).isNotEmpty();
	}

	@Test
	void circuitBreaker_petService_has_expected_config() {
		CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("petService");
		CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

		assertThat(config.getFailureRateThreshold()).isEqualTo(20f);
		assertThat(config.getSlowCallRateThreshold()).isEqualTo(100f);
		assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(60));
		assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(5);
		assertThat(config.getSlidingWindowSize()).isEqualTo(20);
		assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
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
	void retry_petService_external_calls_has_5_attempts() {
		Retry retry = retryRegistry.retry("petService");
		RetryConfig config = retry.getRetryConfig();

		assertThat(config.getMaxAttempts()).isEqualTo(5);
		assertThat(config.getWaitDuration()).isEqualTo(Duration.ofMillis(500));
		assertThat(config.isEnableExponentialBackoff()).isTrue();
		assertThat(config.getExponentialBackoffMultiplier()).isEqualTo(2.0);
	}

	@Test
	void retry_inventoryService_internal_calls_has_2_attempts() {
		Retry retry = retryRegistry.retry("inventoryService");
		RetryConfig config = retry.getRetryConfig();

		assertThat(config.getMaxAttempts()).isEqualTo(2);
		assertThat(config.getWaitDuration()).isEqualTo(Duration.ofMillis(500));
		assertThat(config.isEnableExponentialBackoff()).isTrue();
		assertThat(config.getExponentialBackoffMultiplier()).isEqualTo(2.0);
	}

	@Test
	void rateLimiter_inventoryService_has_expected_config() {
		RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("inventoryService");
		RateLimiterConfig config = rateLimiter.getRateLimiterConfig();

		assertThat(config.getLimitForPeriod()).isEqualTo(5);
		assertThat(config.getLimitRefreshPeriod()).isEqualTo(Duration.ofSeconds(1));
		assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(3));
	}

	@Test
	void rateLimiter_registry_contains_inventoryService() {
		RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("inventoryService");
		assertThat(rateLimiter).isNotNull();
		assertThat(rateLimiter.getName()).isEqualTo("inventoryService");
	}
}
