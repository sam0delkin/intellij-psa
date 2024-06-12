#!/bin/bash

CONTAINER_NAME=app
TS=$(date +%s
# copy PSA context to a tmp location in container
docker cp ${PSA_CONTEXT} ${CONTAINER_NAME}:/tmp/${TS}

# node
docker exec -e PSA_CONTEXT=/tmp/${TS} -e PSA_TYPE -e PSA_LANGUAGE ${CONTAINER_NAME} node /path/to/project/examples/js/psa.js

# php
# docker exec -e PSA_CONTEXT=/tmp/${TS} -e PSA_TYPE -e PSA_LANGUAGE ${CONTAINER_NAME} php /path/to/project/examples/php/psa.php
