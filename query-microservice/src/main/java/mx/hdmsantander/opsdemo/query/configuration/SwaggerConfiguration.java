package mx.hdmsantander.opsdemo.query.configuration;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfiguration {

	@Bean
	public Docket externalApi() {
		return new Docket(DocumentationType.SWAGGER_2).groupName("Main").select()
				.apis(RequestHandlerSelectors.basePackage("mx.hdmsantander.opsdemo.query.controller"))
				.paths(PathSelectors.ant("/v1/**")).build().apiInfo(apiInfo());
	}

	private ApiInfo apiInfo() {
		return new ApiInfo("REST API of the query microservice", "REST API for the tracing demo", "1.0", "",
				new Contact("Fabián Santander", "https://github.com/hdmsantander", "hdmsantander@gmail.com"), "MIT",
				"https://opensource.org/licenses/MIT", Collections.emptyList());
	}

}
