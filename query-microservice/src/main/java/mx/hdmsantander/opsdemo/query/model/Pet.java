package mx.hdmsantander.opsdemo.query.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mx.hdmsantander.opsdemo.query.model.enums.PetStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pet {

	private String id;

	private Category category;

	private String name;

	private List<String> photoUrls;

	private List<Tag> tags;

	private PetStatus status;

}
