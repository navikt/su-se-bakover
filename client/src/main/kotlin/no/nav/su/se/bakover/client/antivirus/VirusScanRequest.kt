package no.nav.su.se.bakover.client.antivirus

data class VirusScanRequest(
    val tittel: String,
    val fil: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VirusScanRequest

        if (tittel != other.tittel) return false
        if (!fil.contentEquals(other.fil)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tittel.hashCode()
        result = 31 * result + fil.contentHashCode()
        return result
    }
}
