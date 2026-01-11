class CreateProducts < ActiveRecord::Migration[8.0]
  def change
    create_table :products do |t|
      t.string :name, null: false
      t.text :description
      t.integer :total_stock, null: false, default: 0
      t.integer :available_stock, null: false, default: 0
      t.decimal :price, precision: 10, scale: 2, null: false
      t.datetime :sale_starts_at, null: false
      t.datetime :sale_ends_at, null: false
      t.string :status, null: false, default: 'scheduled'

      t.timestamps
    end
  end
end
