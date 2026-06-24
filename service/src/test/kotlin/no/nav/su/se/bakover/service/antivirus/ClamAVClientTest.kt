package no.nav.su.se.bakover.service.antivirus

import no.nav.su.se.bakover.client.antivirus.BatchScanResponse
import no.nav.su.se.bakover.client.antivirus.MockClamAVClient
import no.nav.su.se.bakover.client.antivirus.ScanResponse
import no.nav.su.se.bakover.client.antivirus.ScanStatus
import no.nav.su.se.bakover.client.antivirus.VirusScanRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ClamAV Client Tests")
class ClamAVClientTest {

    @Test
    fun `MockClamAVClient always returns OK`() {
        val client = MockClamAVClient()
        val result = client.scan(VirusScanRequest("test.pdf", ByteArray(100)))

        assert(result is ScanResponse.Success)
        assert((result as ScanResponse.Success).result.status == ScanStatus.OK)
    }

    @Test
    fun `MockClamAVClient batch scan returns OK for all files`() {
        val client = MockClamAVClient()
        val files = listOf(
            VirusScanRequest("file1.pdf", ByteArray(100)),
            VirusScanRequest("file2.pdf", ByteArray(200)),
        )

        val result = client.scanBatch(files)

        assert(result is BatchScanResponse.Success)
        val batch = result as BatchScanResponse.Success
        assert(batch.results.size == 2)
        assert(batch.results.all { it.status == ScanStatus.OK })
    }
}
