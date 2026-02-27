package mx.hdmsantander.opsdemo.inventory.configuration;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@Component
@Order(-1)
public class TracingHeaderConfiguration extends GenericFilterBean {

	@Autowired
	private Tracer tracer;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		Span currentSpan = this.tracer.currentSpan();

		if (currentSpan != null) {
			HttpServletResponse resp = (HttpServletResponse) response;
			resp.addHeader("X-Zipkin-Trace-Id", buildTrace(currentSpan.context()));
		}

		chain.doFilter(request, response);
	}

	private String buildTrace(io.micrometer.tracing.TraceContext ctx) {
		String id = String.join("=", "Id", ctx.traceId());
		String parent = String.join("=", "Parent", ctx.spanId());
		String sampled = String.join("=", "Sampled", String.valueOf(ctx.sampled()));
		return String.join(";", id, parent, sampled);
	}
}
