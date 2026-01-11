# This file should ensure the existence of records required to run the application in every environment (production,
# development, test). The code here should be idempotent so that it can be executed at any point in every environment.
# The data can then be loaded with the bin/rails db:seed command (or created alongside the database with db:setup).

puts "🌱 Seeding database..."

products_data = [
  {
    name: 'Limited Edition Sneakers',
    description: '한정판 스니커즈 - 블랙 프라이데이 특별 할인',
    total_stock: 1000,
    available_stock: 1000,
    price: 150000.00,
    sale_starts_at: Time.zone.now + 1.hour,
    sale_ends_at: Time.zone.now + 1.day,
    status: 'scheduled'
  },
  {
    name: 'Premium Winter Jacket',
    description: '프리미엄 겨울 재킷 - 따뜻하고 스타일리시한',
    total_stock: 500,
    available_stock: 500,
    price: 250000.00,
    sale_starts_at: Time.zone.now + 1.hour,
    sale_ends_at: Time.zone.now + 1.day,
    status: 'scheduled'
  },
  {
    name: 'Smart Watch Pro',
    description: '스마트워치 프로 - 최신 기술이 담긴',
    total_stock: 200,
    available_stock: 200,
    price: 350000.00,
    sale_starts_at: Time.zone.now + 1.hour,
    sale_ends_at: Time.zone.now + 1.day,
    status: 'scheduled'
  },
  {
    name: 'Noise Cancelling Headphones',
    description: '노이즈 캔슬링 헤드폰 - 몰입감 있는 사운드',
    total_stock: 750,
    available_stock: 750,
    price: 180000.00,
    sale_starts_at: Time.zone.now + 1.hour,
    sale_ends_at: Time.zone.now + 1.day,
    status: 'scheduled'
  },
  {
    name: 'Travel Backpack Deluxe',
    description: '여행용 백팩 디럭스 - 넉넉한 수납공간',
    total_stock: 300,
    available_stock: 300,
    price: 120000.00,
    sale_starts_at: Time.zone.now + 1.hour,
    sale_ends_at: Time.zone.now + 1.day,
    status: 'scheduled'
  }
]

products_data.each do |product_data|
  product = Product.find_or_initialize_by(name: product_data[:name])
  product.assign_attributes(product_data)

  if product.save
    puts "✅ Created/Updated: #{product.name} (ID: #{product.id}, Partition Key: #{product.partition_key})"
  else
    puts "❌ Failed to create: #{product_data[:name]} - #{product.errors.full_messages.join(', ')}"
  end
end

puts "\n📊 Seeding Summary:"
puts "   Total Products: #{Product.count}"
puts "   Total Stock: #{Product.sum(:total_stock)}"
puts "\n✨ Seeding completed!"
