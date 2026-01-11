class CreatePurchases < ActiveRecord::Migration[8.0]
  def change
    create_table :purchases do |t|
      t.references :purchase_slot, null: false, foreign_key: true
      t.string :payment_status, null: false, default: 'pending'
      t.string :payment_method
      t.string :transaction_id
      t.decimal :amount_paid, precision: 10, scale: 2

      t.timestamps
    end
  end
end
