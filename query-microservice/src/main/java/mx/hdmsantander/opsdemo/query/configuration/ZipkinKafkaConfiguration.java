package mx.hdmsantander.opsdemo.query.configuration;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.kafka.KafkaSender;

@Configuration
@ConditionalOnProperty(name = "management.tracing.export.zipkin.kafka.bootstrap-servers")
@AutoConfigureBefore(ZipkinAutoConfiguration.class)
class ZipkinKafkaConfiguration {

	@Bean
	BytesMessageSender zipkinKafkaSender(Environment env) {
		String bootstrapServers = env.getProperty("management.tracing.export.zipkin.kafka.bootstrap-servers",
				"localhost:9092");
		String topic = env.getProperty("management.tracing.export.zipkin.kafka.topic", "zipkin");
		String serviceName = env.getProperty("spring.application.name", "query-microservice");

		Map<String, Object> producerProps = new HashMap<>();
		producerProps.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
		producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
		producerProps.put(CommonClientConfigs.CLIENT_ID_CONFIG, serviceName);

		return KafkaSender.newBuilder().topic(topic).overrides(producerProps).build();
	}
}
