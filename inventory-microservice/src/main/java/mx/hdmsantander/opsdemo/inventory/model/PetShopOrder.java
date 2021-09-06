package mx.hdmsantander.opsdemo.inventory.model;

import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetShopOrder {

	@Id
	private Integer orderId;

	private String petId;

	private Integer quantity;

	private String shipDate;

	private String status;

	private Boolean complete;

}
