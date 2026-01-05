package com.dopaminestore.product.core.domain.value

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Value object representing monetary amount with currency.
 *
 * Immutable and handles decimal precision for financial calculations.
 */
data class Money(
    val amount: BigDecimal,
    val currency: Currency = Currency.KRW
) : Comparable<Money> {

    init {
        require(amount >= BigDecimal.ZERO) { "Amount cannot be negative: $amount" }
        require(amount.scale() <= 2) { "Amount scale must be at most 2 decimal places: $amount" }
    }

    /**
     * Add another money amount.
     * Both amounts must have the same currency.
     */
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount), currency)
    }

    /**
     * Subtract another money amount.
     * Both amounts must have the same currency.
     */
    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        val result = amount.subtract(other.amount)
        require(result >= BigDecimal.ZERO) { "Subtraction would result in negative amount" }
        return Money(result, currency)
    }

    /**
     * Multiply by a scalar value.
     */
    operator fun times(multiplier: Int): Money {
        return Money(amount.multiply(BigDecimal(multiplier)), currency)
    }

    /**
     * Multiply by a scalar value.
     */
    operator fun times(multiplier: BigDecimal): Money {
        return Money(amount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP), currency)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot perform operation on different currencies: $currency vs ${other.currency}"
        }
    }

    /**
     * Check if amount is zero.
     */
    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0

    /**
     * Check if amount is positive.
     */
    fun isPositive(): Boolean = amount > BigDecimal.ZERO

    override fun toString(): String = "$amount $currency"

    companion object {
        /**
         * Create Money from long value (in minor units, e.g., cents).
         */
        fun fromMinorUnits(minorUnits: Long, currency: Currency = Currency.KRW): Money {
            val amount = BigDecimal(minorUnits).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            return Money(amount, currency)
        }

        /**
         * Create zero money.
         */
        fun zero(currency: Currency = Currency.KRW): Money {
            return Money(BigDecimal.ZERO.setScale(2), currency)
        }

        /**
         * Create Money from string value.
         */
        fun of(amount: String, currency: Currency = Currency.KRW): Money {
            return Money(BigDecimal(amount).setScale(2, RoundingMode.HALF_UP), currency)
        }
    }
}

/**
 * ISO 4217 currency codes.
 */
enum class Currency(val code: String, val symbol: String) {
    KRW("KRW", "₩"),
    USD("USD", "$"),
    EUR("EUR", "€"),
    JPY("JPY", "¥");

    override fun toString(): String = code
}
