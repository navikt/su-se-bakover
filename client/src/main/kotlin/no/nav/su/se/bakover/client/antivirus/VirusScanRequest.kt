package no.nav.su.se.bakover.client.antivirus

@Suppress("ArrayInDataClass")
data class VirusScanRequest(
    val tittel: String,
    val fil: ByteArray,
)
