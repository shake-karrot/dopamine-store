class Product < ApplicationRecord
  # Associations
  has_many :purchase_slots, dependent: :destroy

  # Enums
  enum :status, {
    scheduled: 'scheduled',
    active: 'active',
    sold_out: 'sold_out',
    ended: 'ended'
  }, scopes: false

  # Validations
  validates :name, presence: true
  validates :total_stock, presence: true, numericality: { greater_than_or_equal_to: 0 }
  validates :available_stock, presence: true, numericality: { greater_than_or_equal_to: 0 }
  validates :price, presence: true, numericality: { greater_than: 0 }
  validates :sale_starts_at, presence: true
  validates :sale_ends_at, presence: true
  validates :status, presence: true

  validate :sale_ends_at_after_starts_at

  # Helper method for Kafka partition key
  def partition_key
    "#{name}-#{id}"
  end

  private

  def sale_ends_at_after_starts_at
    return if sale_ends_at.blank? || sale_starts_at.blank?

    if sale_ends_at <= sale_starts_at
      errors.add(:sale_ends_at, 'must be after sale_starts_at')
    end
  end
end
