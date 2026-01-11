class AddIndexesToTables < ActiveRecord::Migration[8.0]
  def change
    # Products indexes
    add_index :products, :status
    add_index :products, :sale_starts_at
    add_index :products, :sale_ends_at

    # PurchaseSlots indexes (product_id index already created by t.references)
    add_index :purchase_slots, :user_id
    add_index :purchase_slots, :status
    add_index :purchase_slots, :queue_position
    add_index :purchase_slots, [:product_id, :user_id]
    add_index :purchase_slots, [:product_id, :status]
    add_index :purchase_slots, :submitted_at

    # Purchases indexes (purchase_slot_id index already created by t.references)
    add_index :purchases, :payment_status
  end
end
