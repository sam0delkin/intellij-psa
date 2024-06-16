#!/bin/bash

if [[ ${PSA_DEBUG} == '1' ]]; then
	PREVIOUS_PID=$(ps aux | grep "node --inspect-brk" | grep -v grep | awk '{print $2}')
	if [[ ${PREVIOUS_PID} ]]; then
		kill -4 ${PREVIOUS_PID}
	fi

	node --inspect-brk $(dirname "$0")/psa.js
else
	node $(dirname "$0")/psa.js
fi
