#!/bin/bash

# me.sh - Get current authenticated user
# Usage: ./me.sh

curl -i -X GET \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  http://localhost:8080/api/auth/me