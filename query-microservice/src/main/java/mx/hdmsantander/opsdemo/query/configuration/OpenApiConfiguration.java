package mx.hdmsantander.opsdemo.query.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenApiConfiguration {

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("REST API of the query microservice")
						.description("REST API for the tracing demo")
						.version("1.0")
						.contact(new Contact()
								.name("Fabián Santander")
								.url("https://github.com/hdmsantander")
								.email("hdmsantander@gmail.com"))
						.license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
	}
}
