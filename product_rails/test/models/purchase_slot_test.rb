require "test_helper"

class PurchaseSlotTest < ActiveSupport::TestCase
  def setup
    @product = Product.create!(
      name: "Test Product",
      description: "Test Description",
      total_stock: 100,
      available_stock: 100,
      price: 10000.00,
      sale_starts_at: Time.zone.now,
      sale_ends_at: Time.zone.now + 1.day,
      status: "scheduled"
    )

    @purchase_slot = PurchaseSlot.new(
      product_id: @product.id,
      user_id: "user-123",
      status: "pending",
      submitted_at: Time.zone.now
    )
  end

  test "should be valid with valid attributes" do
    assert @purchase_slot.valid?
  end

  test "should have integer id after save" do
    @purchase_slot.save!
    assert @purchase_slot.id.is_a?(Integer)
    assert @purchase_slot.id > 0
  end

  test "should require product_id" do
    @purchase_slot.product_id = nil
    assert_not @purchase_slot.valid?
    assert_includes @purchase_slot.errors[:product_id], "can't be blank"
  end

  test "should require user_id" do
    @purchase_slot.user_id = nil
    assert_not @purchase_slot.valid?
    assert_includes @purchase_slot.errors[:user_id], "can't be blank"
  end

  test "should require status" do
    @purchase_slot.status = nil
    assert_not @purchase_slot.valid?
    assert_includes @purchase_slot.errors[:status], "can't be blank"
  end

  test "should require submitted_at" do
    @purchase_slot.submitted_at = nil
    assert_not @purchase_slot.valid?
    assert_includes @purchase_slot.errors[:submitted_at], "can't be blank"
  end

  test "should belong to product" do
    assert_respond_to @purchase_slot, :product
    @purchase_slot.save!
    assert_equal @product, @purchase_slot.product
  end

  test "should have one purchase" do
    assert_respond_to @purchase_slot, :purchase
  end

  test "should define status enum" do
    assert_respond_to @purchase_slot, :status
    assert_respond_to @purchase_slot, :pending?
    assert_respond_to @purchase_slot, :admitted?
    assert_respond_to @purchase_slot, :rejected?
    assert_respond_to @purchase_slot, :expired?
  end

  test "should set status correctly" do
    @purchase_slot.status = "admitted"
    assert @purchase_slot.admitted?
    assert_equal "admitted", @purchase_slot.status
  end

  test "should allow optional queue_position" do
    @purchase_slot.queue_position = nil
    assert @purchase_slot.valid?
  end

  test "should allow optional processed_at" do
    @purchase_slot.processed_at = nil
    assert @purchase_slot.valid?
  end
end
