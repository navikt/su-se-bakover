package no.nav.su.se.bakover.service.antivirus

import no.nav.su.se.bakover.client.antivirus.BatchScanResponse
import no.nav.su.se.bakover.client.antivirus.ClamAVClient
import no.nav.su.se.bakover.client.antivirus.ScanResponse
import no.nav.su.se.bakover.client.antivirus.ScanStatus
import no.nav.su.se.bakover.client.antivirus.VirusScanRequest
import org.slf4j.LoggerFactory

class DefaultVirusScanService(
    private val client: ClamAVClient,
) : VirusScanService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest): ScanResponse {
        val response = client.scan(request)
        logResponse(response, request.tittel)
        return response
    }

    override fun scanBatch(requests: List<VirusScanRequest>): BatchScanResponse {
        val response = client.scanBatch(requests)
        logResponse(response, "${requests.size} files")
        return response
    }

    private fun logResponse(response: ScanResponse, identifier: String) {
        when (response) {
            is ScanResponse.Success -> {
                logger.info("Scan completed: $identifier - Result: ${response.svar.result}")
            }
            is ScanResponse.HttpError -> {
                logger.warn("Scan error: $identifier - ${response.message}")
            }
        }
    }

    private fun logResponse(response: BatchScanResponse, identifier: String) {
        when (response) {
            is BatchScanResponse.Success -> {
                val infected = response.svar.filter { it.result == ScanStatus.FOUND }
                logger.info("Batch scan completed: ${response.svar.size} files, ${infected.size} infected")
            }
            is BatchScanResponse.HttpError -> {
                logger.warn("Batch scan error: $identifier - ${response.message}")
            }
        }
    }
}
