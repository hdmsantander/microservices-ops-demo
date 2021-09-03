package mx.hdmsantander.opsdemo.query.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.AtomicDouble;

import io.micrometer.core.instrument.MeterRegistry;
import mx.hdmsantander.opsdemo.query.data.PetShopOrderRepository;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

@Service
public class PetShopOrderService {

	@Autowired
	private PetShopOrderRepository petShopOrderRepository;

	@Autowired
	private MeterRegistry meterRegistry;
	
	private AtomicDouble orderCount = new AtomicDouble();
	
	public List<PetShopOrder> getAllOrders() {

		List<PetShopOrder> orders = StreamSupport.stream(petShopOrderRepository.findAll().spliterator(), false)
				.collect(Collectors.toList());
		
		orderCount.set(orders.size());
		
		meterRegistry.gauge("orders.size", orderCount);
		
		return orders;

	}

}
