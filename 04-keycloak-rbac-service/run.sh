#!/bin/bash

# run.sh - Simple script to run the Spring Boot application
# Checks if PostgreSQL and Keycloak containers are running before starting

set -e

# Check if PostgreSQL container is running
echo "Checking PostgreSQL container..."
if ! docker ps --format '{{.Names}}' | grep -q "iam_lab_postgres"; then
    echo "❌ PostgreSQL container is not running"
    echo "Please run: docker-compose up -d"
    exit 1
fi
echo "✓ PostgreSQL container is running"

# Check if Keycloak container is running
echo "Checking Keycloak container..."
if ! docker ps --format '{{.Names}}' | grep -q "iam_lab_keycloak"; then
    echo "❌ Keycloak container is not running"
    echo "Please run: docker-compose up -d"
    exit 1
fi
echo "✓ Keycloak container is running"

# Run Spring Boot application
echo ""
echo "Starting Spring Boot application..."
./mvnw spring-boot:run
