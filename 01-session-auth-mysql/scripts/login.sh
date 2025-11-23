#!/bin/bash

# login.sh - Login with username/email and password
# Usage: ./login.sh [usernameOrEmail] [password]

USERNAME_OR_EMAIL=${1:-testuser}
PASSWORD=${2:-password}

curl -i -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "usernameOrEmail=$USERNAME_OR_EMAIL&password=$PASSWORD" \
  -c cookies.txt \
  http://localhost:8080/api/auth/login

echo -e "\nSession cookie saved to cookies.txt"