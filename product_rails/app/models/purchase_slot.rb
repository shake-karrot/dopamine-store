class PurchaseSlot < ApplicationRecord
  # Associations
  belongs_to :product
  has_one :purchase, dependent: :destroy

  # Enums
  enum :status, {
    pending: 'pending',
    admitted: 'admitted',
    rejected: 'rejected',
    expired: 'expired'
  }, scopes: false

  # Validations
  validates :product_id, presence: true
  validates :user_id, presence: true
  validates :status, presence: true
  validates :submitted_at, presence: true
end
