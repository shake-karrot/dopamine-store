package com.dopaminestore.product.core.domain.value

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Money value object.
 *
 * Tests cover:
 * - Money creation and validation
 * - Arithmetic operations (plus, minus, times)
 * - Comparison operations
 * - Currency validation
 * - Helper methods (isZero, isPositive)
 * - Factory methods (zero, of, fromMinorUnits)
 */
class MoneyTest {

    @Test
    fun `should create money with amount and default currency`() {
        val money = Money(BigDecimal("100.00"))

        assertEquals(BigDecimal("100.00"), money.amount)
        assertEquals(Currency.KRW, money.currency)
    }

    @Test
    fun `should create money with specified currency`() {
        val money = Money(BigDecimal("100.00"), Currency.USD)

        assertEquals(BigDecimal("100.00"), money.amount)
        assertEquals(Currency.USD, money.currency)
    }

    @Test
    fun `should throw exception for negative amount`() {
        assertThrows<IllegalArgumentException> {
            Money(BigDecimal("-100.00"))
        }
    }

    @Test
    fun `should throw exception for more than 2 decimal places`() {
        assertThrows<IllegalArgumentException> {
            Money(BigDecimal("100.123"))
        }
    }

    @Test
    fun `should add money with same currency`() {
        val money1 = Money(BigDecimal("100.00"))
        val money2 = Money(BigDecimal("50.00"))

        val result = money1 + money2

        assertEquals(BigDecimal("150.00"), result.amount)
        assertEquals(Currency.KRW, result.currency)
    }

    @Test
    fun `should throw exception when adding money with different currencies`() {
        val krw = Money(BigDecimal("100.00"), Currency.KRW)
        val usd = Money(BigDecimal("50.00"), Currency.USD)

        assertThrows<IllegalArgumentException> {
            krw + usd
        }
    }

    @Test
    fun `should subtract money with same currency`() {
        val money1 = Money(BigDecimal("100.00"))
        val money2 = Money(BigDecimal("30.00"))

        val result = money1 - money2

        assertEquals(BigDecimal("70.00"), result.amount)
        assertEquals(Currency.KRW, result.currency)
    }

    @Test
    fun `should throw exception when subtracting results in negative`() {
        val money1 = Money(BigDecimal("50.00"))
        val money2 = Money(BigDecimal("100.00"))

        assertThrows<IllegalArgumentException> {
            money1 - money2
        }
    }

    @Test
    fun `should throw exception when subtracting money with different currencies`() {
        val krw = Money(BigDecimal("100.00"), Currency.KRW)
        val usd = Money(BigDecimal("50.00"), Currency.USD)

        assertThrows<IllegalArgumentException> {
            krw - usd
        }
    }

    @Test
    fun `should multiply money by integer scalar`() {
        val money = Money(BigDecimal("100.00"))

        val result = money * 3

        assertEquals(BigDecimal("300.00"), result.amount)
        assertEquals(Currency.KRW, result.currency)
    }

    @Test
    fun `should multiply money by decimal scalar with rounding`() {
        val money = Money(BigDecimal("100.00"))

        val result = money * BigDecimal("1.5")

        assertEquals(BigDecimal("150.00"), result.amount)
    }

    @Test
    fun `should round result after multiplication`() {
        val money = Money(BigDecimal("10.00"))

        val result = money * BigDecimal("0.333")

        assertEquals(BigDecimal("3.33"), result.amount)
    }

    @Test
    fun `should compare money with same currency`() {
        val money1 = Money(BigDecimal("100.00"))
        val money2 = Money(BigDecimal("50.00"))
        val money3 = Money(BigDecimal("100.00"))

        assertTrue(money1 > money2)
        assertTrue(money2 < money1)
        assertTrue(money1 >= money3)
        assertTrue(money1 <= money3)
        assertEquals(0, money1.compareTo(money3))
    }

    @Test
    fun `should throw exception when comparing money with different currencies`() {
        val krw = Money(BigDecimal("100.00"), Currency.KRW)
        val usd = Money(BigDecimal("50.00"), Currency.USD)

        assertThrows<IllegalArgumentException> {
            krw > usd
        }
    }

    @Test
    fun `should check if money is positive`() {
        val positive = Money(BigDecimal("100.00"))
        val zero = Money(BigDecimal("0.00"))

        assertTrue(positive.isPositive())
        assertFalse(zero.isPositive())
    }

    @Test
    fun `should check if money is zero`() {
        val zero1 = Money(BigDecimal("0.00"))
        val zero2 = Money.zero()
        val nonZero = Money(BigDecimal("0.01"))

        assertTrue(zero1.isZero())
        assertTrue(zero2.isZero())
        assertFalse(nonZero.isZero())
    }

    @Test
    fun `should create zero money`() {
        val zero = Money.zero(Currency.KRW)

        assertTrue(zero.isZero())
        assertEquals(Currency.KRW, zero.currency)
    }

    @Test
    fun `should create money from string`() {
        val money = Money.of("123.45", Currency.USD)

        assertEquals(BigDecimal("123.45"), money.amount)
        assertEquals(Currency.USD, money.currency)
    }

    @Test
    fun `should throw exception for invalid string amount`() {
        assertThrows<NumberFormatException> {
            Money.of("invalid", Currency.KRW)
        }
    }

    @Test
    fun `should create money from minor units`() {
        val money = Money.fromMinorUnits(12345, Currency.KRW)

        assertEquals(BigDecimal("123.45"), money.amount)
        assertEquals(Currency.KRW, money.currency)
    }

    @Test
    fun `should format money as string`() {
        val krw = Money(BigDecimal("1000.00"), Currency.KRW)
        val usd = Money(BigDecimal("99.99"), Currency.USD)

        assertEquals("1000.00 KRW", krw.toString())
        assertEquals("99.99 USD", usd.toString())
    }

    @Test
    fun `should support all currency types`() {
        val krw = Money(BigDecimal("1000.00"), Currency.KRW)
        val usd = Money(BigDecimal("10.00"), Currency.USD)
        val eur = Money(BigDecimal("10.00"), Currency.EUR)
        val jpy = Money(BigDecimal("1000.00"), Currency.JPY)

        assertEquals(Currency.KRW, krw.currency)
        assertEquals(Currency.USD, usd.currency)
        assertEquals(Currency.EUR, eur.currency)
        assertEquals(Currency.JPY, jpy.currency)
    }

    @Test
    fun `should be immutable`() {
        val original = Money(BigDecimal("100.00"))
        val added = original + Money(BigDecimal("50.00"))

        // Original should not be modified
        assertEquals(BigDecimal("100.00"), original.amount)
        assertEquals(BigDecimal("150.00"), added.amount)
    }

    @Test
    fun `should support equality comparison`() {
        val money1 = Money(BigDecimal("100.00"), Currency.KRW)
        val money2 = Money(BigDecimal("100.00"), Currency.KRW)
        val money3 = Money(BigDecimal("100.00"), Currency.USD)
        val money4 = Money(BigDecimal("50.00"), Currency.KRW)

        assertEquals(money1, money2)
        assertEquals(money1.hashCode(), money2.hashCode())

        assertTrue(money1 != money3) // Different currency
        assertTrue(money1 != money4) // Different amount
    }

    @Test
    fun `should have currency codes and symbols`() {
        assertEquals("KRW", Currency.KRW.code)
        assertEquals("₩", Currency.KRW.symbol)
        assertEquals("USD", Currency.USD.code)
        assertEquals("$", Currency.USD.symbol)
        assertEquals("EUR", Currency.EUR.code)
        assertEquals("€", Currency.EUR.symbol)
        assertEquals("JPY", Currency.JPY.code)
        assertEquals("¥", Currency.JPY.symbol)
    }
}
