# frozen_string_literal: true

module Api
  module V1
    # PurchasesController handles actual purchase completion
    # Step 2 of the 2-step purchase process: completing purchase with approved slot
    class PurchasesController < ApplicationController
      # POST /api/v1/purchases
      # Completes a purchase using an approved slot
      #
      # Request body:
      # {
      #   "slot_id": 123456,
      #   "user_id": "user-123",
      #   "payment_method": "credit_card",
      #   "payment_details": {
      #     "card_token": "tok_abc123"
      #   }
      # }
      #
      # Response (200 OK):
      # {
      #   "purchase_id": 789,
      #   "slot_id": 123456,
      #   "status": "completed",
      #   "transaction_id": "txn_xyz789",
      #   "amount_paid": 150000
      # }
      #
      # Response (400 Bad Request):
      # {
      #   "error": "Invalid slot status",
      #   "message": "Slot must be in ADMITTED status to complete purchase"
      # }
      def create
        trace_id = generate_trace_id

        Rails.logger.info("[#{trace_id}] Purchase request received - slot_id: #{purchase_params[:slot_id]}, user_id: #{purchase_params[:user_id]}")

        # Find the purchase slot
        slot = PurchaseSlot.find(purchase_params[:slot_id])

        # Validate slot ownership
        unless slot.user_id == purchase_params[:user_id]
          return render json: {
            error: "Unauthorized",
            message: "This slot does not belong to you"
          }, status: :forbidden
        end

        # Validate slot status
        unless slot.admitted?
          return render json: {
            error: "Invalid slot status",
            message: "Slot must be in ADMITTED status to complete purchase. Current status: #{slot.status}"
          }, status: :bad_request
        end

        # Check if slot has expired
        if slot.expires_at && slot.expires_at < Time.current
          slot.update!(status: :expired)
          return render json: {
            error: "Slot expired",
            message: "Your purchase slot has expired"
          }, status: :bad_request
        end

        # Check if purchase already exists for this slot
        if slot.purchase.present?
          return render json: {
            error: "Already purchased",
            message: "This slot has already been used for a purchase"
          }, status: :bad_request
        end

        # Get product info for amount
        product = slot.product

        # Process payment (simplified - in real system would integrate with payment gateway)
        transaction_id = generate_transaction_id
        amount_paid = product.price

        # Create purchase record
        purchase = Purchase.create!(
          purchase_slot_id: slot.id,
          payment_status: :completed,
          payment_method: purchase_params[:payment_method],
          transaction_id: transaction_id,
          amount_paid: amount_paid
        )

        Rails.logger.info("[#{trace_id}] Purchase completed successfully - purchase_id: #{purchase.id}, slot_id: #{slot.id}, transaction_id: #{transaction_id}, amount_paid: #{amount_paid}")

        render json: {
          purchase_id: purchase.id,
          slot_id: slot.id,
          status: purchase.payment_status,
          transaction_id: purchase.transaction_id,
          amount_paid: purchase.amount_paid,
          product_id: product.id,
          product_name: product.name
        }, status: :ok

      rescue ActiveRecord::RecordNotFound
        Rails.logger.warn("[#{trace_id}] Purchase slot not found - slot_id: #{purchase_params[:slot_id]}")

        render json: {
          error: "Slot not found",
          message: "The requested purchase slot does not exist"
        }, status: :not_found

      rescue ActiveRecord::RecordInvalid => e
        Rails.logger.error("[#{trace_id}] Validation error creating purchase - errors: #{e.record.errors.full_messages.join(', ')}")

        render json: {
          error: "Validation failed",
          message: e.record.errors.full_messages.join(", ")
        }, status: :unprocessable_entity

      rescue StandardError => e
        Rails.logger.error("[#{trace_id}] Unexpected error processing purchase - error: #{e.message}, error_class: #{e.class.name}, slot_id: #{purchase_params[:slot_id]}")

        render json: {
          error: "Internal server error",
          message: "An unexpected error occurred. Please try again later."
        }, status: :internal_server_error
      end

      private

      def purchase_params
        params.require(:purchase).permit(
          :slot_id,
          :user_id,
          :payment_method,
          payment_details: [:card_token]
        )
      end

      def generate_trace_id
        "trace-#{SecureRandom.uuid}"
      end

      def generate_transaction_id
        "txn-#{SecureRandom.uuid}"
      end
    end
  end
end
