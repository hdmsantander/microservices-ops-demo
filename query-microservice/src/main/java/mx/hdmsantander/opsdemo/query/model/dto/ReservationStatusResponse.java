package mx.hdmsantander.opsdemo.query.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStatusResponse {

	private String reservationId;
	private String petId;
	private String expiresAt;
	private boolean valid;
}
