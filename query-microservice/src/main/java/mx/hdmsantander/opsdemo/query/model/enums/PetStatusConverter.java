package mx.hdmsantander.opsdemo.query.model.enums;

import org.springframework.core.convert.converter.Converter;

public class PetStatusConverter implements Converter<String, PetStatus> {

	@Override
	public PetStatus convert(String source) {
		return PetStatus.valueOf(source.toUpperCase());
	}

}
