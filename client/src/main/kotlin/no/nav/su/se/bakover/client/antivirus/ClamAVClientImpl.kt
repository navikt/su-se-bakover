package no.nav.su.se.bakover.client.antivirus

import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.common.deserialize
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

class ClamAVClientImpl(
    private val antivirusUrl: String,
) : ClamAVClient {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest): ScanResponse {
        return try {
            val (_, _, result) = "$antivirusUrl/scan"
                .httpPost(
                    listOf(
                        "file" to ByteArrayInputStream(request.fil),
                    ),
                )
                .responseString()

            result.fold(
                { json ->
                    try {
                        val scanResults = deserialize<List<ScanResult>>(json)
                        val scanResult = scanResults.firstOrNull()
                            ?: throw IllegalStateException("Empty response from ClamAV for file ${request.tittel}")

                        when (scanResult.result) {
                            ScanStatus.FOUND -> logger.warn("Virus detected in ${request.tittel}: ${scanResult.virus}")
                            ScanStatus.ERROR -> logger.error("Scan error for ${request.tittel}: ${scanResult.error}")
                            ScanStatus.OK -> logger.info("File scan OK: ${request.tittel}")
                        }

                        ScanResponse.Success(scanResult)
                    } catch (e: Exception) {
                        logger.error("Deserialization failed for ClamAV response", e)
                        ScanResponse.HttpError("Scanning failed for ${request.tittel}: ${e.message}")
                    }
                },
                { error ->
                    logger.error("HTTP error scanning file: ${request.tittel}", error)
                    ScanResponse.HttpError("Scanning failed for ${request.tittel}: ${error.message}")
                },
            )
        } catch (e: Exception) {
            logger.error("Failed to scan file: ${request.tittel}", e)
            ScanResponse.HttpError("Scanning failed for ${request.tittel}: ${e.message}")
        }
    }

    override fun scanBatch(requests: List<VirusScanRequest>): BatchScanResponse {
        return try {
            val filesToUpload = requests.map { request ->
                "file" to ByteArrayInputStream(request.fil)
            }

            val (_, _, result) = "$antivirusUrl/scan"
                .httpPost(filesToUpload)
                .responseString()

            result.fold(
                { json ->
                    try {
                        val scanResults = deserialize<List<ScanResult>>(json)

                        scanResults.forEach { scanResult ->
                            when (scanResult.result) {
                                ScanStatus.FOUND -> logger.warn("Virus detected in ${scanResult.filename}: ${scanResult.virus}")
                                ScanStatus.ERROR -> logger.error("Scan error for ${scanResult.filename}: ${scanResult.error}")
                                ScanStatus.OK -> logger.info("File scan OK: ${scanResult.filename}")
                            }
                        }

                        BatchScanResponse.Success(scanResults)
                    } catch (e: Exception) {
                        logger.error("Deserialization failed for ClamAV batch response", e)
                        BatchScanResponse.HttpError("Batch scanning failed: ${e.message}")
                    }
                },
                { error ->
                    logger.error("HTTP error scanning batch", error)
                    BatchScanResponse.HttpError("Batch scanning failed: ${error.message}")
                },
            )
        } catch (e: Exception) {
            logger.error("Failed to scan batch", e)
            BatchScanResponse.HttpError("Batch scanning failed: ${e.message}")
        }
    }
}
