package no.nav.su.se.bakover.service.antivirus

import no.nav.su.se.bakover.client.antivirus.BatchScanResponse
import no.nav.su.se.bakover.client.antivirus.ClamAVClient
import no.nav.su.se.bakover.client.antivirus.ScanResponse
import no.nav.su.se.bakover.client.antivirus.ScanResult
import no.nav.su.se.bakover.client.antivirus.ScanStatus
import no.nav.su.se.bakover.domain.antivirus.VirusScanRequest
import no.nav.su.se.bakover.domain.antivirus.VirusScanResult
import no.nav.su.se.bakover.domain.antivirus.VirusScanService
import org.slf4j.LoggerFactory

class VirusFileScannerService(
    private val client: ClamAVClient,
) : VirusScanService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest) {
        handleResponse(client.scan(request), request.tittel)
    }

    override fun scanBatch(requests: List<VirusScanRequest>): List<VirusScanResult> {
        return handleBatchResponse(client.scanBatch(requests), requests.size)
    }

    private fun handleResponse(response: ScanResponse, identifier: String) {
        when (response) {
            is ScanResponse.Success -> {
                when (response.svar.result) {
                    ScanStatus.OK -> logger.info("Virus scan OK for file: {}", identifier)
                    ScanStatus.FOUND -> {
                        logger.warn("Malicious content detected in file: {}. File rejected.", identifier)
                        throw IllegalArgumentException("PDF inneholder malware/virus og kan ikke lagres")
                    }
                    ScanStatus.ERROR -> {
                        logger.error("Virus scanner reported error for file: {}. Error: {}", identifier, response.svar.error)
                        throw IllegalStateException("Virus scan reported error: ${response.svar.error}")
                    }
                }
            }
            is ScanResponse.HttpError -> {
                logger.error("HTTP request to virus scanner failed for file: {}. Error: {}", identifier, response.message)
                throw IllegalStateException("HTTP error during virus scan: ${response.message}")
            }
        }
    }

    private fun handleBatchResponse(response: BatchScanResponse, numberOfFiles: Int): List<VirusScanResult> {
        return when (response) {
            is BatchScanResponse.Success -> {
                response.svar.map { scanResult -> mapBatchResult(scanResult) }.also { results ->
                    val errorCount = results.count { it.status == VirusScanResult.Status.ERROR }
                    logger.info("Batch scan completed: {} files, {} with errors", numberOfFiles, errorCount)
                }
            }
            is BatchScanResponse.HttpError -> {
                logger.error("HTTP request to batch virus scanner failed for {} files. Error: {}", numberOfFiles, response.message)
                throw IllegalStateException("HTTP error during batch virus scan: ${response.message}")
            }
        }
    }

    private fun mapBatchResult(scanResult: ScanResult): VirusScanResult {
        return when (scanResult.result) {
            ScanStatus.OK -> {
                logger.info("Virus scan OK for file: {}", scanResult.filename)
                VirusScanResult(filename = scanResult.filename, status = VirusScanResult.Status.OK)
            }
            ScanStatus.FOUND -> {
                logger.warn("Malicious content detected in file: {}. File rejected.", scanResult.filename)
                VirusScanResult(
                    filename = scanResult.filename,
                    status = VirusScanResult.Status.ERROR,
                    message = "PDF inneholder malware/virus og kan ikke lagres",
                )
            }
            ScanStatus.ERROR -> {
                logger.error("Virus scanner reported error for file: {}. Error: {}", scanResult.filename, scanResult.error)
                VirusScanResult(
                    filename = scanResult.filename,
                    status = VirusScanResult.Status.ERROR,
                    message = scanResult.error,
                )
            }
        }
    }
}
