package mx.hdmsantander.opsdemo.query.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@ExtendWith(MockitoExtension.class)
class TracingHeaderConfigurationTest {

	@Mock
	private Tracer tracer;

	@Mock
	private Span span;

	@Mock
	private io.micrometer.tracing.TraceContext traceContext;

	private TracingHeaderConfiguration filter;

	@BeforeEach
	void setUp() {
		filter = new TracingHeaderConfiguration();
		ReflectionTestUtils.setField(filter, "tracer", tracer);
	}

	@Test
	void doFilter_addsTraceHeader_whenSpanPresent() throws IOException, jakarta.servlet.ServletException {
		when(tracer.currentSpan()).thenReturn(span);
		when(span.context()).thenReturn(traceContext);
		when(traceContext.traceId()).thenReturn("abc123");
		when(traceContext.spanId()).thenReturn("span456");
		when(traceContext.sampled()).thenReturn(Boolean.TRUE);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		String header = response.getHeader("X-Zipkin-Trace-Id");
		assertThat(header).isNotNull()
				.contains("Id=abc123")
				.contains("Parent=span456")
				.contains("Sampled=true");
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void doFilter_continuesChain_whenNoSpan() throws IOException, jakarta.servlet.ServletException {
		when(tracer.currentSpan()).thenReturn(null);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getHeader("X-Zipkin-Trace-Id")).isNull();
		assertThat(chain.getRequest()).isNotNull();
	}
}
