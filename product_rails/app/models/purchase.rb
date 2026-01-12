class Purchase < ApplicationRecord
  # Associations
  belongs_to :purchase_slot

  # Enums
  enum :payment_status, {
    pending: 'pending',
    completed: 'completed',
    failed: 'failed'
  }, scopes: false

  # Validations
  validates :purchase_slot_id, presence: true
  validates :payment_status, presence: true
  validates :amount_paid, numericality: { greater_than_or_equal_to: 0 }, allow_nil: true
end
