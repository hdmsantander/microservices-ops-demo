# Kafka Connect with Elasticsearch Sink Connector
FROM confluentinc/cp-kafka-connect:7.6.0

USER root
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:14.0.10
USER appuser
