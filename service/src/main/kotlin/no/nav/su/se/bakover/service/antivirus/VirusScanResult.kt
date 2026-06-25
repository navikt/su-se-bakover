package no.nav.su.se.bakover.service.antivirus

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
