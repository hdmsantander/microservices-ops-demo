package mx.hdmsantander.opsdemo.query.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

	private String reservationId;
	private String petId;
	private String expiresAt;
}
