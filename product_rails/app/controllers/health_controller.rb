class HealthController < ApplicationController
  def index
    health_status = {
      status: 'ok',
      timestamp: Time.current.iso8601,
      services: {
        database: check_database,
        redis: check_redis,
        kafka: check_kafka
      }
    }

    status_code = health_status[:services].values.all? { |s| s[:status] == 'ok' } ? :ok : :service_unavailable

    render json: health_status, status: status_code
  end

  private

  def check_database
    ActiveRecord::Base.connection.execute('SELECT 1')
    { status: 'ok' }
  rescue => e
    { status: 'error', message: e.message }
  end

  def check_redis
    redis = Redis.new(url: ENV.fetch('REDIS_URL', 'redis://localhost:6379/0'))
    redis.ping
    { status: 'ok' }
  rescue => e
    { status: 'error', message: e.message }
  end

  def check_kafka
    # Simple check - verify Karafka is configured
    if KarafkaApp.config.kafka.present?
      { status: 'ok' }
    else
      { status: 'error', message: 'Kafka not configured' }
    end
  rescue => e
    { status: 'error', message: e.message }
  end
end
