# frozen_string_literal: true

require "test_helper"

module Api
  module V1
    class PurchaseSlotsControllerTest < ActionDispatch::IntegrationTest
      setup do
        @product = products(:active_product)
        @user_id = "test-user-123"
      end

      test "should create purchase slot and return 202" do
        # Mock Kafka producer
        KafkaProducerService.stub :publish_purchase_attempt, true do
          post api_v1_purchase_slots_url, params: {
            purchase_slot: {
              product_id: @product.id,
              user_id: @user_id
            }
          }, as: :json

          assert_response :accepted
          json_response = JSON.parse(response.body)

          assert json_response["slot_id"].present?
          assert_equal "pending", json_response["status"]
          assert_equal "Your purchase slot request has been queued", json_response["message"]
          assert json_response["status_url"].present?
        end
      end

      test "should return 404 when product not found" do
        post api_v1_purchase_slots_url, params: {
          purchase_slot: {
            product_id: 99999,
            user_id: @user_id
          }
        }, as: :json

        assert_response :not_found
        json_response = JSON.parse(response.body)
        assert_equal "Product not found", json_response["error"]
      end

      test "should return 422 when product is not active" do
        inactive_product = products(:scheduled_product)

        post api_v1_purchase_slots_url, params: {
          purchase_slot: {
            product_id: inactive_product.id,
            user_id: @user_id
          }
        }, as: :json

        assert_response :unprocessable_entity
        json_response = JSON.parse(response.body)
        assert_equal "Product not available", json_response["error"]
      end

      test "should return 503 when kafka publish fails" do
        # Mock Kafka producer to raise error
        KafkaProducerService.stub :publish_purchase_attempt, ->(*) { raise KafkaProducerService::PublishError, "Kafka down" } do
          post api_v1_purchase_slots_url, params: {
            purchase_slot: {
              product_id: @product.id,
              user_id: @user_id
            }
          }, as: :json

          assert_response :service_unavailable
          json_response = JSON.parse(response.body)
          assert_equal "Service unavailable", json_response["error"]
        end
      end

      test "should get purchase slot status" do
        slot = purchase_slots(:pending_slot)

        get api_v1_purchase_slot_url(slot), as: :json

        assert_response :success
        json_response = JSON.parse(response.body)

        assert_equal slot.id, json_response["slot_id"]
        assert_equal slot.status, json_response["status"]
        assert_equal slot.product_id, json_response["product_id"]
      end

      test "should return 404 when slot not found" do
        get api_v1_purchase_slot_url(99999), as: :json

        assert_response :not_found
        json_response = JSON.parse(response.body)
        assert_equal "Slot not found", json_response["error"]
      end

      test "should show admitted status with expires_at" do
        slot = purchase_slots(:admitted_slot)

        get api_v1_purchase_slot_url(slot), as: :json

        assert_response :success
        json_response = JSON.parse(response.body)

        assert_equal "admitted", json_response["status"]
        assert json_response["expires_at"].present?
        assert_equal "You can now proceed to purchase", json_response["message"]
      end

      test "should show rejected status with message" do
        slot = purchase_slots(:rejected_slot)

        get api_v1_purchase_slot_url(slot), as: :json

        assert_response :success
        json_response = JSON.parse(response.body)

        assert_equal "rejected", json_response["status"]
        assert_equal "Product is sold out", json_response["message"]
      end
    end
  end
end
