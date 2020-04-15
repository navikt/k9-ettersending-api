#!/usr/bin/env sh

echo "Importing API-GW Key"

KEY="API_GATEWAY_API_KEY"
VALUE=$(cat /var/run/secrets/nais.io/apigw/x-nav-apiKey)

echo "- exporting API_GATEWAY_API_KEY"
export "$KEY"="$VALUE"