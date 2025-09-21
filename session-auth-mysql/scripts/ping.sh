#!/bin/bash

# ping.sh - Access the secure ping endpoint
# Usage: ./ping.sh

curl -i -X GET \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  http://localhost:8080/api/secure/ping