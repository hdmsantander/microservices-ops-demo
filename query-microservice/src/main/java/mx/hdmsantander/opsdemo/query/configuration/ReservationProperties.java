package mx.hdmsantander.opsdemo.query.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "reservation")
public class ReservationProperties {

	private int ttlSeconds = 300;
	private int cleanupIntervalSeconds = 60;
}
