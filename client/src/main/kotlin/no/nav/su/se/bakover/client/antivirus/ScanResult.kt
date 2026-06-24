package no.nav.su.se.bakover.client.antivirus

enum class ScanStatus {
    FOUND,
    OK,
    ERROR,
}

data class ScanResult(
    val filename: String? = null,
    val result: ScanStatus,
    val virus: String = "",
    val error: String = "",
)

sealed interface ScanResponse {
    data class Success(val svar: ScanResult) : ScanResponse
    data class HttpError(val message: String) : ScanResponse
}

sealed interface BatchScanResponse {
    data class Success(val svar: List<ScanResult>) : BatchScanResponse
    data class HttpError(val message: String) : BatchScanResponse
}
