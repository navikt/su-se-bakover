package no.nav.su.se.bakover.client.antivirus

enum class ScanStatus {
    FOUND,
    OK,
    ERROR,
}

data class ScanResult(
    val filename: String,
    val status: ScanStatus,
)

sealed interface ScanResponse {
    data class Success(val result: ScanResult) : ScanResponse
    data class HttpError(val message: String) : ScanResponse
}

sealed interface BatchScanResponse {
    data class Success(val results: List<ScanResult>) : BatchScanResponse
    data class HttpError(val message: String) : BatchScanResponse
}
