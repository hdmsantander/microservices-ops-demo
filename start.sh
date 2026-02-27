#!/bin/bash

echo "Packaging microservices"
mvn package -f query-microservice/pom.xml && mvn package -f inventory-microservice/pom.xml && echo "Success building sources! Starting environment" && docker-compose build && docker-compose up