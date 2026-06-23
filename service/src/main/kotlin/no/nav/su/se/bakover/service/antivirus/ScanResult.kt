package no.nav.su.se.bakover.service.antivirus

enum class ScanStatus {
    FOUND,
    OK,
    ERROR,
}

data class ScanResult(
    val Filename: String,
    val Result: ScanStatus,
)

sealed interface VirusScanResponse {
    data class Success(val result: ScanResult) : VirusScanResponse
    data class Batch(val results: List<ScanResult>) : VirusScanResponse
    data class Error(val message: String) : VirusScanResponse
}
