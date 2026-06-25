package no.nav.su.se.bakover.domain.antivirus

interface VirusScanService {
    fun scan(request: VirusScanRequest)
    fun scanBatch(requests: List<VirusScanRequest>): List<VirusScanResult>
}
