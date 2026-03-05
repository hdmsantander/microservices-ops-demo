# Kafka Connect with Elasticsearch Sink Connector
# Base: confluentinc/cp-kafka-connect (Confluent Platform Kafka Connect)
# Connector: https://www.confluent.io/hub/confluentinc/kafka-connect-elasticsearch
FROM confluentinc/cp-kafka-connect:7.6.0

USER root
# Elasticsearch Sink Connector; use 14.1.2 (14.0.10 no longer on Confluent Hub)
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:14.1.2
# Add curl for healthcheck
RUN (microdnf install -y curl 2>/dev/null && microdnf clean all) || \
    (yum install -y curl 2>/dev/null && yum clean all) || true
USER appuser
