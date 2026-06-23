package no.nav.su.se.bakover.service.antivirus

interface VirusScanService {
    fun scan(request: VirusScanRequest): VirusScanResponse
    fun scanBatch(requests: List<VirusScanRequest>): VirusScanResponse
}
