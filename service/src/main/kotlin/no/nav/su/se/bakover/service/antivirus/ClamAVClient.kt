package no.nav.su.se.bakover.service.antivirus

interface ClamAVClient {
    fun scan(request: VirusScanRequest): VirusScanResponse
    fun scanBatch(requests: List<VirusScanRequest>): VirusScanResponse
}
