#!/bin/bash

SERVICE_NAME=app
COMPOSE_OPTIONS="-f docker-compose.dev.yml"
TS=$(date +%s
# copy PSA context to a tmp location in container
docker compose ${COMPOSE_OPTIONS} cp ${PSA_CONTEXT} ${SERVICE_NAME}:/tmp/${TS}

# node
docker compose ${COMPOSE_OPTIONS} exec -e PSA_CONTEXT=/tmp/${TS} -e PSA_TYPE -e PSA_LANGUAGE ${SERVICE_NAME} node /path/to/project/examples/js/psa.js

# php
# docker compose ${COMPOSE_OPTIONS} exec -e PSA_CONTEXT=/tmp/${TS} -e PSA_TYPE -e PSA_LANGUAGE ${SERVICE_NAME} php /path/to/project/examples/php/psa.php
