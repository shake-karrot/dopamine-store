# frozen_string_literal: true

require "test_helper"

module Api
  module V1
    class PurchasesControllerTest < ActionDispatch::IntegrationTest
      setup do
        @admitted_slot = purchase_slots(:admitted_slot)
        @pending_slot = purchase_slots(:pending_slot)
        @rejected_slot = purchase_slots(:rejected_slot)
      end

      test "should complete purchase with admitted slot" do
        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: @admitted_slot.id,
            user_id: @admitted_slot.user_id,
            payment_method: "credit_card",
            payment_details: {
              card_token: "tok_test_123"
            }
          }
        }, as: :json

        assert_response :success
        json_response = JSON.parse(response.body)

        assert json_response["purchase_id"].present?
        assert_equal @admitted_slot.id, json_response["slot_id"]
        assert_equal "completed", json_response["status"]
        assert json_response["transaction_id"].present?
        assert json_response["amount_paid"].present?
      end

      test "should return 403 when slot does not belong to user" do
        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: @admitted_slot.id,
            user_id: "wrong-user-id",
            payment_method: "credit_card"
          }
        }, as: :json

        assert_response :forbidden
        json_response = JSON.parse(response.body)
        assert_equal "Unauthorized", json_response["error"]
      end

      test "should return 400 when slot is not admitted" do
        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: @pending_slot.id,
            user_id: @pending_slot.user_id,
            payment_method: "credit_card"
          }
        }, as: :json

        assert_response :bad_request
        json_response = JSON.parse(response.body)
        assert_equal "Invalid slot status", json_response["error"]
      end

      test "should return 400 when slot is rejected" do
        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: @rejected_slot.id,
            user_id: @rejected_slot.user_id,
            payment_method: "credit_card"
          }
        }, as: :json

        assert_response :bad_request
        json_response = JSON.parse(response.body)
        assert_equal "Invalid slot status", json_response["error"]
      end

      test "should return 404 when slot not found" do
        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: 99999,
            user_id: "test-user",
            payment_method: "credit_card"
          }
        }, as: :json

        assert_response :not_found
        json_response = JSON.parse(response.body)
        assert_equal "Slot not found", json_response["error"]
      end

      test "should return 400 when slot has expired" do
        expired_slot = purchase_slots(:expired_slot)

        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: expired_slot.id,
            user_id: expired_slot.user_id,
            payment_method: "credit_card"
          }
        }, as: :json

        assert_response :bad_request
        json_response = JSON.parse(response.body)
        assert_match(/expired/i, json_response["message"])
      end

      test "should return 400 when purchase already exists for slot" do
        # Create first purchase
        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: @admitted_slot.id,
            user_id: @admitted_slot.user_id,
            payment_method: "credit_card"
          }
        }, as: :json

        assert_response :success

        # Try to create second purchase with same slot
        post api_v1_purchases_url, params: {
          purchase: {
            slot_id: @admitted_slot.id,
            user_id: @admitted_slot.user_id,
            payment_method: "credit_card"
          }
        }, as: :json

        assert_response :bad_request
        json_response = JSON.parse(response.body)
        assert_equal "Already purchased", json_response["error"]
      end
    end
  end
end
