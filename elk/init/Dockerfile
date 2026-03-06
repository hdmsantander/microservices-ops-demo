# ELK initialization: topic creation, connector registration, Kibana provisioning.
# Uses Kafka tools and curl; runs on host network to reach Kafka/Connect/Kibana.
FROM confluentinc/cp-kafka:7.6.0

USER root
# Add curl for REST API calls (UBI base uses microdnf)
RUN (microdnf install -y curl 2>/dev/null && microdnf clean all) || \
    (yum install -y curl 2>/dev/null && yum clean all) || \
    (apt-get update && apt-get install -y curl 2>/dev/null && rm -rf /var/lib/apt/lists/*) || true

WORKDIR /elk-init

COPY init/docker-init.sh /elk-init/docker-init.sh
COPY kibana-dashboards/ /elk-init/dashboards/
RUN chmod +x /elk-init/docker-init.sh

ENTRYPOINT ["/elk-init/docker-init.sh"]
