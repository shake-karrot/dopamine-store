# frozen_string_literal: true

# KafkaProducerService is responsible for sending messages to Kafka topics
# using the WaterDrop producer. It handles message publishing for the FCFS
# (First-Come-First-Served) purchase slot system.
class KafkaProducerService
  class PublishError < StandardError; end

  PURCHASE_ATTEMPTS_TOPIC = "purchase.attempts"

  # Publishes a purchase slot request to the Kafka purchase.attempts topic
  #
  # @param slot_id [Integer] The ID of the purchase slot
  # @param user_id [String] The user's identifier
  # @param product_id [Integer] The product's ID
  # @param product_partition_key [String] The partition key for Kafka (e.g., "Product Name-1")
  # @param trace_id [String] The request trace ID for logging
  # @return [Boolean] true if message was successfully published
  # @raise [PublishError] if publishing fails
  def self.publish_purchase_attempt(slot_id:, user_id:, product_id:, product_partition_key:, trace_id:)
    message = {
      slotId: slot_id,
      userId: user_id,
      productId: product_id,
      productPartitionKey: product_partition_key,
      submittedAt: Time.current.iso8601(3),
      traceId: trace_id
    }

    begin
      # Use WaterDrop producer to send message
      # Partition key ensures all requests for the same product go to the same partition
      Karafka.producer.produce_sync(
        topic: PURCHASE_ATTEMPTS_TOPIC,
        payload: message.to_json,
        partition_key: product_partition_key,
        headers: {
          "trace_id" => trace_id,
          "content_type" => "application/json"
        }
      )

      Rails.logger.info("[#{trace_id}] Kafka message published - topic: #{PURCHASE_ATTEMPTS_TOPIC}, slot_id: #{slot_id}, user_id: #{user_id}, product_id: #{product_id}")

      true
    rescue StandardError => e
      Rails.logger.error("[#{trace_id}] Failed to publish Kafka message - error: #{e.message}, error_class: #{e.class.name}, slot_id: #{slot_id}, user_id: #{user_id}, product_id: #{product_id}")

      raise PublishError, "Failed to publish message to Kafka: #{e.message}"
    end
  end

  # Publishes purchase result to the purchase.results topic
  #
  # @param slot_id [Integer] The ID of the purchase slot
  # @param user_id [String] The user's identifier
  # @param product_id [Integer] The product's ID
  # @param position [Integer] The queue position
  # @param status [String] The slot status (ADMITTED or REJECTED)
  # @param trace_id [String] The request trace ID
  def self.publish_purchase_result(slot_id:, user_id:, product_id:, position:, status:, trace_id:, expires_at: nil)
    message = {
      slotId: slot_id,
      userId: user_id,
      productId: product_id,
      position: position,
      status: status,
      processedAt: Time.current.iso8601(3),
      expiresAt: expires_at&.iso8601(3),
      traceId: trace_id
    }.compact

    begin
      Karafka.producer.produce_async(
        topic: "purchase.results",
        payload: message.to_json,
        headers: {
          "trace_id" => trace_id,
          "content_type" => "application/json"
        }
      )

      Rails.logger.info("[#{trace_id}] Purchase result published - slot_id: #{slot_id}, status: #{status}")

      true
    rescue StandardError => e
      Rails.logger.error("[#{trace_id}] Failed to publish purchase result - error: #{e.message}, slot_id: #{slot_id}")

      false
    end
  end
end
