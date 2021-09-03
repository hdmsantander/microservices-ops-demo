package mx.hdmsantander.opsdemo.query.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import mx.hdmsantander.opsdemo.query.data.PetShopOrderRepository;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

@Service
public class PetShopOrderService {

	@Autowired
	private PetShopOrderRepository petShopOrderRepository;

	@Autowired
	private MeterRegistry meterRegistry;

	public List<PetShopOrder> getAllOrders() {

		List<PetShopOrder> orders = StreamSupport.stream(petShopOrderRepository.findAll().spliterator(), false)
				.collect(Collectors.toList());

		return meterRegistry.gauge("orders.size", Collections.emptyList(), orders, List::size);

	}

}
