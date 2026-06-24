package no.nav.su.se.bakover.client.antivirus

import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.common.deserialize
import org.slf4j.LoggerFactory

class ClamAVClientImpl(
    private val antivirusUrl: String,
) : ClamAVClient {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest): ScanResponse {
        return try {
            val (req, response, result) = "$antivirusUrl/scan"
                .httpPost()
                .body(request.fil)
                .responseString()

            result.fold(
                { json ->
                    val scanResult = deserialize<Map<String, String>>(json)
                    val parsed = parseResponse(scanResult, request.tittel)
                    ScanResponse.Success(parsed)
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
            val results = requests.map { request ->
                val (_, _, result) = "$antivirusUrl/scan"
                    .httpPost()
                    .body(request.fil)
                    .responseString()

                result.fold(
                    { json ->
                        val scanResult = deserialize<Map<String, String>>(json)
                        parseResponse(scanResult, request.tittel)
                    },
                    { error ->
                        throw error
                    },
                )
            }
            BatchScanResponse.Success(results)
        } catch (e: Exception) {
            logger.error("Failed to scan batch", e)
            BatchScanResponse.HttpError("Batch scanning failed: ${e.message}")
        }
    }

    private fun parseResponse(response: Map<String, String>, filename: String): ScanResult {
        val resultField = response["Result"] ?: "ERROR"
        val status = when (resultField) {
            "OK" -> ScanStatus.OK
            "FOUND" -> ScanStatus.FOUND
            else -> ScanStatus.ERROR
        }
        return ScanResult(
            filename = filename,
            status = status,
        )
    }
}
