package mx.hdmsantander.opsdemo.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EntityScan("mx.hdmsantander.opsdemo.inventory.model")
@ComponentScan(basePackages = "mx.hdmsantander.opsdemo.inventory")
@EnableScheduling
@SpringBootApplication
public class QueryApplication {

	public static void main(String[] args) {
		SpringApplication.run(QueryApplication.class, args);
	}

}
