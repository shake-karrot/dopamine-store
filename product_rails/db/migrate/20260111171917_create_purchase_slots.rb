class CreatePurchaseSlots < ActiveRecord::Migration[8.0]
  def change
    create_table :purchase_slots do |t|
      t.references :product, null: false, foreign_key: true
      t.string :user_id, null: false
      t.bigint :queue_position
      t.string :status, null: false, default: 'pending'
      t.datetime :submitted_at, null: false
      t.datetime :processed_at
      t.datetime :expires_at
      t.string :trace_id

      t.timestamps
    end
  end
end
