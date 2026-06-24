package no.nav.su.se.bakover.service.antivirus

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.antivirus.BatchScanResponse
import no.nav.su.se.bakover.client.antivirus.ClamAVClient
import no.nav.su.se.bakover.client.antivirus.ScanResponse
import no.nav.su.se.bakover.client.antivirus.ScanResult
import no.nav.su.se.bakover.client.antivirus.ScanStatus
import no.nav.su.se.bakover.client.antivirus.VirusScanRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class DefaultVirusScanServiceTest {

    @Test
    fun `scan throws IllegalArgumentException when malware is found`() {
        val client = mock<ClamAVClient> {
            on { scan(any()) } doReturn ScanResponse.Success(
                ScanResult(filename = "test.pdf", result = ScanStatus.FOUND, virus = "Eicar"),
            )
        }
        val service = DefaultVirusScanService(client)

        assertThrows<IllegalArgumentException> {
            service.scan(VirusScanRequest("test.pdf", ByteArray(10)))
        }
    }

    @Test
    fun `scanBatch maps found and scanner errors to ERROR results`() {
        val client = mock<ClamAVClient> {
            on { scanBatch(any()) } doReturn BatchScanResponse.Success(
                listOf(
                    ScanResult(filename = "ok.pdf", result = ScanStatus.OK),
                    ScanResult(filename = "infected.pdf", result = ScanStatus.FOUND, virus = "Eicar"),
                    ScanResult(filename = "broken.pdf", result = ScanStatus.ERROR, error = "timeout"),
                ),
            )
        }
        val service = DefaultVirusScanService(client)

        val result = service.scanBatch(
            listOf(
                VirusScanRequest("ok.pdf", ByteArray(10)),
                VirusScanRequest("infected.pdf", ByteArray(10)),
                VirusScanRequest("broken.pdf", ByteArray(10)),
            ),
        )

        result shouldBe listOf(
            VirusScanResult(filename = "ok.pdf", status = VirusScanResult.Status.OK),
            VirusScanResult(
                filename = "infected.pdf",
                status = VirusScanResult.Status.ERROR,
                message = "PDF inneholder malware/virus og kan ikke lagres",
            ),
            VirusScanResult(filename = "broken.pdf", status = VirusScanResult.Status.ERROR, message = "timeout"),
        )
    }
}
