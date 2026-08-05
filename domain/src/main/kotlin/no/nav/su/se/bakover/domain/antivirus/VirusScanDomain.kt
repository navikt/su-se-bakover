package no.nav.su.se.bakover.domain.antivirus

@Suppress("ArrayInDataClass")
data class VirusScanRequest(
    val tittel: String,
    val fil: ByteArray,
)

data class VirusScanResult(
    val filename: String?,
    val status: Status,
    val message: String? = null,
) {
    enum class Status {
        OK,
        ERROR,
    }
}
