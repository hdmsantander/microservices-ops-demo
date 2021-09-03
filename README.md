# Microservices OPS demo

This repository holds a Spring Boot OPS demo with the following components:

- Two microservices that perform requests to the [Swagger's PetStore](https://petstore.swagger.io/) and comunicate with each other using HTTP requests and events using [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream).
- A ZipKin server that register traces and dependencies from the microservices operations, provided by [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth).
- A Prometheus server that polls the microservices to register metrics gathered from [Spring Boot's actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html).
- A Kafka cluster to comunicate the microservices and the tracer.

The demo can be started by executing the following command:

```BASH
./start.sh
```

The Zipkin server, Prometheus server and Kafka cluster can be started standalone by running:

```BASH
docker-compose -f docker-compose-minimal.yml up
```

## Query microservice

![Query microservice](.img/1.png)

This microservice performs queries to the inventory microservice and the pet shop API. It supports the following operations:

- `GET /v1/inventory` This operation queries the inventory endpoint of the inventory microservice and returns the result.
- `GET /v1/pet` This operation queries the list of pets from the pet shop API and returns the results.
- `POST /v1/pet/{id}/adopt` This operation performs the "adoption" of a pet from the shop. It requires a valid ID from the pet shop and it triggers an adoption event, which is consumed by the inventory microservice, which then in turn emits an event.
- `GET /v1/orders` This operation queries the service's database to get a list of all the orders currently registered in the system. The orders are created from events wich the inventory microservice emits.

The Swagger page is accesible at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Inventory microservice

![Inventory microservice](.img/2.png)

This microservice performs queries to the pet shop API. This service is used by the query service to perform some queries. It supports the following operations:

- `GET /v1/inventory` This operation queries the inventory endpoint of the pet shop API and returns the result. It is intended to be used by the query microservice.

It also performs a scheduled query of the inventory of the pet shop API to "update" the inventory of the shop in the microservice ecosystem. The service performs a query of orders (generated randomly as integers in the range of 1-10) to the [orders endpoint](https://petstore.swagger.io/v2/store/order) of the pet shop API, triggering an event if the order exists, this event is consumed by the query microservice which in turn updates the entity in question in its database.

The Swagger page is accesible at [http://localhost:8079/swagger-ui.html](http://localhost:8079/swagger-ui.html)

## Prometheus server

![Prometheus service](.img/3.png)

The prometheus server is accesible at [http://localhost:9090](http://localhost:9090)

### Example of metrics reported

Here are some examples of the metrics registered.

#### Orders updated in total

In the [OrderService.java](https://github.com/hdmsantander/microservices-ops-demo/blob/a2718edffddaceb66ad7045835c7b0705419c365/inventory-microservice/src/main/java/mx/hdmsantander/opsdemo/inventory/service/OrderService.java#L58) there is a section of code that updates a _Counter_ style metric.

```JAVA
if (responseEntity.getStatusCode().is2xxSuccessful()) {
  log.info("Request was successful! Emitting event to update orders!");
  meterRegistry.counter("orders.updated").increment();
  orderEventService.send(responseEntity.getBody());
}
```

![Orders updated](.img/8.png)

#### Time taken to adopt a pet

In the [PetService.java](https://github.com/hdmsantander/microservices-ops-demo/blob/a2718edffddaceb66ad7045835c7b0705419c365/query-microservice/src/main/java/mx/hdmsantander/opsdemo/query/service/PetService.java#L61) there is an annotation that enables a _Timer_ style metric for the [method](https://github.com/hdmsantander/microservices-ops-demo/blob/a2718edffddaceb66ad7045835c7b0705419c365/query-microservice/src/main/java/mx/hdmsantander/opsdemo/query/service/PetService.java#L62) that performs pet adoptions.

```JAVA
@Timed(value = "pet.query.time", description = "Time taken to query and return the pet shop list for all pets")
```

![Orders updated](.img/9.png)

#### Amount of orders in the system

In the [PetShopOrderService.java](https://github.com/hdmsantander/microservices-ops-demo/blob/a2718edffddaceb66ad7045835c7b0705419c365/query-microservice/src/main/java/mx/hdmsantander/opsdemo/query/service/PetShopOrderService.java#L29) there is a _Gauge_ style metric that reports the size of the list containing all the orders currently in the query system's database.

```JAVA
private AtomicDouble orderCount = new AtomicDouble();

	public List<PetShopOrder> getAllOrders() {

		List<PetShopOrder> orders = StreamSupport.stream(petShopOrderRepository.findAll().spliterator(), false)
				.collect(Collectors.toList());

		orderCount.set(orders.size());

		meterRegistry.gauge("orders.size", orderCount);

		return orders;

	}
```

![Orders updated](.img/10.png)

## Zipkin server

![Zipkin server](.img/4.png)

The Zipkin server is accesible at [http://localhost:9411](http://localhost:9411)

### Example of traces registered

Here are some examples of the traces registered.

#### Scheduled order retrieval

The inventory microservice performs a scheduled retrieval of the orders in the pet shop API. If it finds an order, it emits an event that the query microservice consumes to update its database.

![Refresh orders](.img/6.png)

#### Pet adoption

The query microservice performs a GET of the pet ID to the pet shop API, and if the ID of the pet exists it emits an event which in turn is consumed by the inventory microservice.

![Adoption](.img/7.png)

## Kafka server

![Kafka server](.img/5.png)

An interface to see the topcs and events found in the kafka server is accesible at [http://localhost:3030](http://localhost:3030)
