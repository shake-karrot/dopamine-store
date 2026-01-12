require "test_helper"

class ProductTest < ActiveSupport::TestCase
  def setup
    @product = Product.new(
      name: "Test Product",
      description: "Test Description",
      total_stock: 100,
      available_stock: 100,
      price: 10000.00,
      sale_starts_at: Time.zone.now,
      sale_ends_at: Time.zone.now + 1.day,
      status: "scheduled"
    )
  end

  test "should be valid with valid attributes" do
    assert @product.valid?
  end

  test "should have integer id after save" do
    @product.save!
    assert @product.id.is_a?(Integer)
    assert @product.id > 0
  end

  test "should require name" do
    @product.name = nil
    assert_not @product.valid?
    assert_includes @product.errors[:name], "can't be blank"
  end

  test "should require total_stock" do
    @product.total_stock = nil
    assert_not @product.valid?
    assert_includes @product.errors[:total_stock], "can't be blank"
  end

  test "should require non-negative total_stock" do
    @product.total_stock = -1
    assert_not @product.valid?
    assert_includes @product.errors[:total_stock], "must be greater than or equal to 0"
  end

  test "should require available_stock" do
    @product.available_stock = nil
    assert_not @product.valid?
    assert_includes @product.errors[:available_stock], "can't be blank"
  end

  test "should require positive price" do
    @product.price = 0
    assert_not @product.valid?
    assert_includes @product.errors[:price], "must be greater than 0"
  end

  test "should require sale_starts_at" do
    @product.sale_starts_at = nil
    assert_not @product.valid?
    assert_includes @product.errors[:sale_starts_at], "can't be blank"
  end

  test "should require sale_ends_at after sale_starts_at" do
    @product.sale_ends_at = @product.sale_starts_at - 1.hour
    assert_not @product.valid?
    assert_includes @product.errors[:sale_ends_at], "must be after sale_starts_at"
  end

  test "should have many purchase_slots" do
    assert_respond_to @product, :purchase_slots
  end

  test "should define status enum" do
    assert_respond_to @product, :status
    assert_respond_to @product, :scheduled?
    assert_respond_to @product, :active?
    assert_respond_to @product, :sold_out?
    assert_respond_to @product, :ended?
  end

  test "should set status correctly" do
    @product.status = "active"
    assert @product.active?
    assert_equal "active", @product.status
  end

  test "should generate partition key" do
    @product.save!
    expected_key = "#{@product.name}-#{@product.id}"
    assert_equal expected_key, @product.partition_key
  end
end
