package mx.hdmsantander.opsdemo.query.data;

import org.springframework.data.repository.PagingAndSortingRepository;

import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

public interface PetShopOrderRepository extends PagingAndSortingRepository<PetShopOrder, Integer> {

}
