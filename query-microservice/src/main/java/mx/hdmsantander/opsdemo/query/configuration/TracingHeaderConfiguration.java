package mx.hdmsantander.opsdemo.query.configuration;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.instrument.web.SleuthWebProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

@Component
@Order(SleuthWebProperties.TRACING_FILTER_ORDER + 1)
public class TracingHeaderConfiguration extends GenericFilterBean {

	private final Tracer tracer;

	@Autowired
	public TracingHeaderConfiguration(Tracer tracer) {
		this.tracer = tracer;
	}

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

	private String buildTrace(TraceContext t) {

		String id = String.join("=", "Id", t.traceId());
		String parent = String.join("=", "Parent", t.parentId());
		String sampled = String.join("=", "Sampled", String.valueOf(t.sampled()));

		return String.join(";", id, parent, sampled);

	}

}
