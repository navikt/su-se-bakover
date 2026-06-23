package no.nav.su.se.bakover.service.antivirus

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.http.Headers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class KtorClamAVClient(
    private val httpClient: HttpClient,
    private val antivirusUrl: String,
) : ClamAVClient {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun scan(request: VirusScanRequest): VirusScanResponse {
        return runBlocking {
            try {
                val response = httpClient.post("$antivirusUrl/scan") {
                    formData {
                        append(
                            "file",
                            request.fil,
                            Headers.build {
                                append("Content-Disposition", "filename=${request.tittel}")
                            },
                        )
                    }
                }.body<Map<String, String>>()

                val result = parseResponse(response, request.tittel)
                VirusScanResponse.Success(result)
            } catch (e: Exception) {
                logger.error("Failed to scan file: ${request.tittel}", e)
                VirusScanResponse.Error("Scanning failed for ${request.tittel}: ${e.message}")
            }
        }
    }

    override fun scanBatch(requests: List<VirusScanRequest>): VirusScanResponse {
        return runBlocking {
            try {
                val results = requests.map { request ->
                    val response = httpClient.post("$antivirusUrl/scan") {
                        formData {
                            append(
                                "file",
                                request.fil,
                                Headers.build {
                                    append("Content-Disposition", "filename=${request.tittel}")
                                },
                            )
                        }
                    }.body<Map<String, String>>()
                    parseResponse(response, request.tittel)
                }
                VirusScanResponse.Batch(results)
            } catch (e: Exception) {
                logger.error("Failed to scan batch", e)
                VirusScanResponse.Error("Batch scanning failed: ${e.message}")
            }
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
            Filename = filename,
            Result = status,
        )
    }
}
