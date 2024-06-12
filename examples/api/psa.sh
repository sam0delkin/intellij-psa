#!/bin/bash

# set your API endpoint
#API_ENDPOINT=http://localhost:12345/_psa
API_ENDPOINT=https://666969682e964a6dfed5074e.mockapi.io/_psa/1

# use POST in your app, GET is used as example for mockapi
#API_METHOD=POST
API_METHOD=GET

curl -X ${API_METHOD} \
  -H 'Content-Type: application/json' \
  -H "PSA-LANGUAGE: ${PSA_LANGUAGE}" \
  -H "PSA-TYPE: ${PSA_TYPE}" \
  -H "PSA-DEBUG: ${PSA_DEBUG}" \
  -d @${PSA_CONTEXT} ${API_ENDPOINT}
