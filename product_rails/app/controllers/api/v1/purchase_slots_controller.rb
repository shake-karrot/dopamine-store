# frozen_string_literal: true

module Api
  module V1
    # PurchaseSlotsController handles purchase slot requests
    # Step 1 of the 2-step purchase process: acquiring purchase permission
    class PurchaseSlotsController < ApplicationController
      # POST /api/v1/purchase_slots
      # Creates a purchase slot request and queues it via Kafka
      #
      # Request body:
      # {
      #   "product_id": 1,
      #   "user_id": "user-123"
      # }
      #
      # Response (202 Accepted):
      # {
      #   "slot_id": 123456,
      #   "status": "pending",
      #   "message": "Your purchase slot request has been queued",
      #   "status_url": "/api/v1/purchase_slots/123456"
      # }
      def create
        trace_id = generate_trace_id

        Rails.logger.info("[#{trace_id}] Purchase slot request received - product_id: #{purchase_slot_params[:product_id]}, user_id: #{purchase_slot_params[:user_id]}")

        # Find the product
        product = Product.find(purchase_slot_params[:product_id])

        # Validate product is available for purchase
        unless product.active?
          return render json: {
            error: "Product not available",
            message: "This product is not currently available for purchase"
          }, status: :unprocessable_entity
        end

        # Create purchase slot record
        slot = PurchaseSlot.create!(
          product_id: product.id,
          user_id: purchase_slot_params[:user_id],
          status: :pending,
          submitted_at: Time.current,
          trace_id: trace_id
        )

        # Publish to Kafka
        begin
          KafkaProducerService.publish_purchase_attempt(
            slot_id: slot.id,
            user_id: slot.user_id,
            product_id: product.id,
            product_partition_key: product.partition_key,
            trace_id: trace_id
          )

          Rails.logger.info("[#{trace_id}] Purchase slot created and queued - slot_id: #{slot.id}, product_id: #{product.id}, user_id: #{slot.user_id}")

          render json: {
            slot_id: slot.id,
            status: slot.status,
            message: "Your purchase slot request has been queued",
            status_url: api_v1_purchase_slot_path(slot)
          }, status: :accepted

        rescue KafkaProducerService::PublishError => e
          Rails.logger.error("[#{trace_id}] Kafka publish failed for slot - slot_id: #{slot.id}, error: #{e.message}")

          # Mark slot as failed (we could add a 'failed' status to enum if needed)
          slot.destroy

          render json: {
            error: "Service unavailable",
            message: "Unable to queue your request. Please try again later."
          }, status: :service_unavailable
        end

      rescue ActiveRecord::RecordNotFound
        Rails.logger.warn("[#{trace_id}] Product not found - product_id: #{purchase_slot_params[:product_id]}")

        render json: {
          error: "Product not found",
          message: "The requested product does not exist"
        }, status: :not_found

      rescue ActiveRecord::RecordInvalid => e
        Rails.logger.error("[#{trace_id}] Validation error creating slot - errors: #{e.record.errors.full_messages.join(', ')}")

        render json: {
          error: "Validation failed",
          message: e.record.errors.full_messages.join(", ")
        }, status: :unprocessable_entity
      end

      # GET /api/v1/purchase_slots/:id
      # Retrieves the status of a purchase slot
      #
      # Response (pending):
      # {
      #   "slot_id": 123456,
      #   "status": "pending",
      #   "position": null,
      #   "message": "Your request is being processed"
      # }
      #
      # Response (admitted):
      # {
      #   "slot_id": 123456,
      #   "status": "admitted",
      #   "position": 87,
      #   "expires_at": "2026-01-15T10:15:00.000Z",
      #   "message": "You can now proceed to purchase"
      # }
      #
      # Response (rejected):
      # {
      #   "slot_id": 123456,
      #   "status": "rejected",
      #   "position": 1523,
      #   "message": "Product is sold out"
      # }
      def show
        slot = PurchaseSlot.find(params[:id])

        response = {
          slot_id: slot.id,
          status: slot.status,
          position: slot.queue_position,
          product_id: slot.product_id
        }

        # Add status-specific information
        case slot.status
        when "pending"
          response[:message] = "Your request is being processed"

        when "admitted"
          response[:expires_at] = slot.expires_at&.iso8601(3)
          response[:message] = "You can now proceed to purchase"

        when "rejected"
          response[:message] = "Product is sold out"

        when "expired"
          response[:message] = "Your purchase slot has expired"
        end

        render json: response, status: :ok

      rescue ActiveRecord::RecordNotFound
        render json: {
          error: "Slot not found",
          message: "The requested purchase slot does not exist"
        }, status: :not_found
      end

      private

      def purchase_slot_params
        params.require(:purchase_slot).permit(:product_id, :user_id)
      end

      def generate_trace_id
        "trace-#{SecureRandom.uuid}"
      end
    end
  end
end
