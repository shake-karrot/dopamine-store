#!/bin/bash

# Kafka topic creation script for product_rails
# This script creates the necessary Kafka topics for the FCFS order processing system

KAFKA_BROKER=${KAFKA_BROKER:-"kafka:9093"}

echo "Waiting for Kafka to be ready..."
sleep 10

echo "Creating Kafka topics..."

# Create topics with appropriate configurations
kafka-topics --create \
  --bootstrap-server "$KAFKA_BROKER" \
  --topic example \
  --partitions 3 \
  --replication-factor 1 \
  --if-not-exists \
  --config retention.ms=604800000

echo "Kafka topics created successfully!"

# List all topics to verify
echo "Current topics:"
kafka-topics --list --bootstrap-server "$KAFKA_BROKER"
