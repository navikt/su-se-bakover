package no.nav.su.se.bakover.service.antivirus

import org.slf4j.LoggerFactory

class MockClamAVClient : ClamAVClient {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest): VirusScanResponse {
        logger.info("Mock scan (always OK): ${request.tittel}")
        return VirusScanResponse.Success(
            ScanResult(
                Filename = request.tittel,
                Result = ScanStatus.OK,
            ),
        )
    }

    override fun scanBatch(requests: List<VirusScanRequest>): VirusScanResponse {
        logger.info("Mock batch scan (always OK): ${requests.size} files")
        return VirusScanResponse.Batch(
            requests.map {
                ScanResult(
                    Filename = it.tittel,
                    Result = ScanStatus.OK,
                )
            },
        )
    }
}
