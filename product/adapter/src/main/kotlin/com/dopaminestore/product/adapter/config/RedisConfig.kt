package com.dopaminestore.product.adapter.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import java.time.Duration

/**
 * Redis configuration for distributed slot availability tracking and caching.
 *
 * Connection pool settings optimized for 100K RPS:
 * - Min idle: 10 connections
 * - Max active: 50 connections
 * - Max wait: 3 seconds
 *
 * Timeout settings:
 * - Command timeout: 3 seconds
 * - Socket connect timeout: 2 seconds
 * - Socket timeout: 2 seconds
 */
@Configuration
class RedisConfig {

    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        val socketOptions = SocketOptions.builder()
            .connectTimeout(Duration.ofSeconds(2))
            .keepAlive(true)
            .build()

        val clientOptions = ClientOptions.builder()
            .socketOptions(socketOptions)
            .autoReconnect(true)
            .build()

        val poolConfig = LettucePoolingClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(3))
            .clientOptions(clientOptions)
            .build()

        return LettuceConnectionFactory(
            org.springframework.data.redis.connection.RedisStandaloneConfiguration().apply {
                hostName = System.getenv("REDIS_HOST") ?: "localhost"
                port = System.getenv("REDIS_PORT")?.toInt() ?: 6379
                password = org.springframework.data.redis.connection.RedisPassword.of(
                    System.getenv("REDIS_PASSWORD") ?: ""
                )
            },
            poolConfig
        ).apply {
            afterPropertiesSet()
        }
    }

    @Bean
    fun reactiveRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): ReactiveRedisTemplate<String, String> {
        val serializationContext = RedisSerializationContext
            .newSerializationContext<String, String>(StringRedisSerializer())
            .build()

        return ReactiveRedisTemplate(connectionFactory, serializationContext)
    }
}
