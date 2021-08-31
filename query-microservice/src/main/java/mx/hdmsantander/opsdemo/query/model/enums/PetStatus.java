package mx.hdmsantander.opsdemo.query.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PetStatus {

	AVAILABLE("available"), PENDING("pending"), SOLD("sold");

	private String code;

	private PetStatus(String code) {
		this.code = code;
	}

	@JsonValue
	public String getCode() {
		return code;
	}

}
