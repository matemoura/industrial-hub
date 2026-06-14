#!/bin/sh
set -e
# Substitui apenas ${BACKEND_URL}, preservando variáveis internas do nginx ($host, $remote_addr, etc.)
envsubst '${BACKEND_URL}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
