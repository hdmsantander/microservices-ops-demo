package mx.hdmsantander.opsdemo.query.service;

import java.util.List;
import java.util.stream.StreamSupport;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import mx.hdmsantander.opsdemo.query.data.PetShopOrderRepository;
import mx.hdmsantander.opsdemo.query.model.PetShopOrder;

@Service
public class PetShopOrderService {

	@Autowired
	private PetShopOrderRepository petShopOrderRepository;

	@Autowired
	private MeterRegistry meterRegistry;

	@PostConstruct
	void registerGauge() {
		Gauge.builder("orders.size", petShopOrderRepository, r -> (double) r.count())
				.description("Number of orders in the system")
				.register(meterRegistry);
	}

	public List<PetShopOrder> getAllOrders() {
		return StreamSupport.stream(petShopOrderRepository.findAll(Sort.unsorted()).spliterator(), false).toList();
	}
}
