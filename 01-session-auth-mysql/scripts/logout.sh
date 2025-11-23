#!/bin/bash

# logout.sh - Logout and invalidate session
# Usage: ./logout.sh

curl -i -X POST \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  http://localhost:8080/api/auth/logout

echo -e "\nSession cookie cleared"