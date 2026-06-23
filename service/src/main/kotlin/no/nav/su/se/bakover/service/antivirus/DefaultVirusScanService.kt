package no.nav.su.se.bakover.service.antivirus

import org.slf4j.LoggerFactory

class DefaultVirusScanService(
    private val client: ClamAVClient,
) : VirusScanService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest): VirusScanResponse {
        val response = client.scan(request)
        logResponse(response, request.tittel)
        return response
    }

    override fun scanBatch(requests: List<VirusScanRequest>): VirusScanResponse {
        val response = client.scanBatch(requests)
        logResponse(response, "${requests.size} files")
        return response
    }

    private fun logResponse(response: VirusScanResponse, identifier: String) {
        when (response) {
            is VirusScanResponse.Success -> {
                logger.info("Scan completed: $identifier - Result: ${response.result.Result}")
            }
            is VirusScanResponse.Batch -> {
                val infected = response.results.filter { it.Result == ScanStatus.FOUND }
                logger.info("Batch scan completed: ${response.results.size} files, ${infected.size} infected")
            }
            is VirusScanResponse.Error -> {
                logger.warn("Scan error: $identifier - ${response.message}")
            }
        }
    }
}
