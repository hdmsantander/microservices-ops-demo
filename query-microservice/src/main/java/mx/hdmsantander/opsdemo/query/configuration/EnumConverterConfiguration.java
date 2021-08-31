package mx.hdmsantander.opsdemo.query.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import mx.hdmsantander.opsdemo.query.model.enums.PetStatusConverter;

@Configuration
public class EnumConverterConfiguration implements WebMvcConfigurer {

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(new PetStatusConverter());
	}

}
