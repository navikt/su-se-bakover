package no.nav.su.se.bakover.web

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DirectMemoryMetricsTest {

    @Test
    fun `parseJvmMemorySizeToBytes should parse supported formats`() {
        val cases = mapOf(
            "1024" to 1024L,
            "1k" to 1024L,
            "1K" to 1024L,
            "1m" to 1024L * 1024L,
            "1mb" to 1024L * 1024L,
            "1G" to 1024L * 1024L * 1024L,
            "1t" to 1024L * 1024L * 1024L * 1024L,
            " 512m " to 512L * 1024L * 1024L,
        )

        cases.forEach { (input, expected) ->
            DirectMemoryMetrics.parseJvmMemorySizeToBytes(input) shouldBe expected
        }
    }

    @Test
    fun `parseJvmMemorySizeToBytes should return null for invalid values`() {
        val invalidValues = listOf(
            "",
            " ",
            "-1",
            "-1m",
            "abc",
            "1p",
            "1.5m",
            "m",
        )

        invalidValues.forEach { input ->
            DirectMemoryMetrics.parseJvmMemorySizeToBytes(input) shouldBe null
        }
    }

    @Test
    fun `parseJvmMemorySizeToBytes should return null on overflow`() {
        DirectMemoryMetrics.parseJvmMemorySizeToBytes("${Long.MAX_VALUE}k") shouldBe null
    }

    @Test
    fun `getConfiguredMaxDirectMemoryBytes should parse value from input arguments`() {
        val inputArguments = listOf(
            "-Xms256m",
            "-XX:MaxDirectMemorySize=512m",
            "-Xmx2048m",
        )

        DirectMemoryMetrics.getConfiguredMaxDirectMemoryBytes(inputArguments) shouldBe 512L * 1024L * 1024L
    }

    @Test
    fun `getConfiguredMaxDirectMemoryBytes should return null when missing`() {
        val inputArguments = listOf(
            "-Xms256m",
            "-Xmx2048m",
        )

        DirectMemoryMetrics.getConfiguredMaxDirectMemoryBytes(inputArguments) shouldBe null
    }

    @Test
    fun `getConfiguredMaxDirectMemoryBytes should return null on invalid value`() {
        val inputArguments = listOf("-XX:MaxDirectMemorySize=1.5m")

        DirectMemoryMetrics.getConfiguredMaxDirectMemoryBytes(inputArguments) shouldBe null
    }

    @Test
    fun `formatMiB should format to one decimal place`() {
        DirectMemoryMetrics.formatMiB(0L) shouldBe "0.0"
        DirectMemoryMetrics.formatMiB(1L) shouldBe "0.0"
        DirectMemoryMetrics.formatMiB(1024L * 1024L) shouldBe "1.0"
        DirectMemoryMetrics.formatMiB(1572864L) shouldBe "1.5"
        DirectMemoryMetrics.formatMiB(512L * 1024L * 1024L) shouldBe "512.0"
    }
}
