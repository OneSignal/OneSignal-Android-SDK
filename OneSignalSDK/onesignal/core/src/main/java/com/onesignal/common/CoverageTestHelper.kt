package com.onesignal.common

/**
 * A simple utility class designed to test code coverage.
 * This class contains various methods with different branches and conditions
 * to verify that test coverage tools are working correctly.
 */
object CoverageTestHelper {
    /**
     * Adds two numbers together.
     */
    fun add(a: Int, b: Int): Int {
        return a + b
    }

    /**
     * Subtracts the second number from the first.
     */
    fun subtract(a: Int, b: Int): Int {
        return a - b
    }

    /**
     * Returns the maximum of two numbers.
     */
    fun max(a: Int, b: Int): Int {
        return if (a > b) a else b
    }

    /**
     * Returns the minimum of two numbers.
     */
    fun min(a: Int, b: Int): Int {
        return if (a < b) a else b
    }

    /**
     * Checks if a number is positive.
     */
    fun isPositive(number: Int): Boolean {
        return number > 0
    }

    /**
     * Checks if a number is negative.
     */
    fun isNegative(number: Int): Boolean {
        return number < 0
    }

    /**
     * Checks if a number is zero.
     */
    fun isZero(number: Int): Boolean {
        return number == 0
    }

    /**
     * Returns a greeting message based on the time of day.
     * @param hour The hour of the day (0-23)
     */
    fun getGreeting(hour: Int): String {
        return when {
            hour < 0 -> "Invalid hour"
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            hour < 24 -> "Good evening"
            else -> "Invalid hour"
        }
    }

    /**
     * Calculates the factorial of a number (only for small numbers).
     */
    fun factorial(n: Int): Long {
        if (n < 0) {
            return -1
        }
        if (n == 0 || n == 1) {
            return 1
        }
        var result = 1L
        for (i in 2..n) {
            result *= i
        }
        return result
    }

    /**
     * Checks if a string is empty or null.
     */
    fun isEmptyOrNull(str: String?): Boolean {
        return str == null || str.isEmpty()
    }

    /**
     * Returns the length of a string, or -1 if null.
     */
    fun getLength(str: String?): Int {
        return str?.length ?: -1
    }

    fun isGood(value: Boolean): Boolean {
        return value
    }
}
