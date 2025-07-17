#!/bin/bash

if [[ '1' == ${PSA_DEBUG} ]]; then
  php -dxdebug.mode=debug $(dirname "$0")/psa.php
else
  php -dxdebug.mode=off $(dirname "$0")/psa.php
fi
