package no.nav.su.se.bakover.client.antivirus

import no.nav.su.se.bakover.domain.antivirus.VirusScanRequest
import org.slf4j.LoggerFactory

class MockClamAVClient : ClamAVClient {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest): ScanResponse {
        logger.info("Mock scan (always OK): ${request.tittel}")
        return ScanResponse.Success(
            ScanResult(
                filename = request.tittel,
                result = ScanStatus.OK,
                virus = "",
                error = "",
            ),
        )
    }

    override fun scanBatch(requests: List<VirusScanRequest>): BatchScanResponse {
        logger.info("Mock batch scan (always OK): ${requests.size} files")
        return BatchScanResponse.Success(
            requests.map {
                ScanResult(
                    filename = it.tittel,
                    result = ScanStatus.OK,
                    virus = "",
                    error = "",
                )
            },
        )
    }
}
