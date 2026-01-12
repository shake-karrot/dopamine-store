# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.0].define(version: 2026_01_11_172131) do
  # These are extensions that must be enabled in order to support this database
  enable_extension "pg_catalog.plpgsql"

  create_table "products", force: :cascade do |t|
    t.string "name", null: false
    t.text "description"
    t.integer "total_stock", default: 0, null: false
    t.integer "available_stock", default: 0, null: false
    t.decimal "price", precision: 10, scale: 2, null: false
    t.datetime "sale_starts_at", null: false
    t.datetime "sale_ends_at", null: false
    t.string "status", default: "scheduled", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["sale_ends_at"], name: "index_products_on_sale_ends_at"
    t.index ["sale_starts_at"], name: "index_products_on_sale_starts_at"
    t.index ["status"], name: "index_products_on_status"
  end

  create_table "purchase_slots", force: :cascade do |t|
    t.bigint "product_id", null: false
    t.string "user_id", null: false
    t.bigint "queue_position"
    t.string "status", default: "pending", null: false
    t.datetime "submitted_at", null: false
    t.datetime "processed_at"
    t.datetime "expires_at"
    t.string "trace_id"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["product_id", "status"], name: "index_purchase_slots_on_product_id_and_status"
    t.index ["product_id", "user_id"], name: "index_purchase_slots_on_product_id_and_user_id"
    t.index ["product_id"], name: "index_purchase_slots_on_product_id"
    t.index ["queue_position"], name: "index_purchase_slots_on_queue_position"
    t.index ["status"], name: "index_purchase_slots_on_status"
    t.index ["submitted_at"], name: "index_purchase_slots_on_submitted_at"
    t.index ["user_id"], name: "index_purchase_slots_on_user_id"
  end

  create_table "purchases", force: :cascade do |t|
    t.bigint "purchase_slot_id", null: false
    t.string "payment_status", default: "pending", null: false
    t.string "payment_method"
    t.string "transaction_id"
    t.decimal "amount_paid", precision: 10, scale: 2
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["payment_status"], name: "index_purchases_on_payment_status"
    t.index ["purchase_slot_id"], name: "index_purchases_on_purchase_slot_id"
  end

  add_foreign_key "purchase_slots", "products"
  add_foreign_key "purchases", "purchase_slots"
end
