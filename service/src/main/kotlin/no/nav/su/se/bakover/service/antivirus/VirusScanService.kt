package no.nav.su.se.bakover.service.antivirus

import no.nav.su.se.bakover.client.antivirus.VirusScanRequest

interface VirusScanService {
    fun scan(request: VirusScanRequest)
    fun scanBatch(requests: List<VirusScanRequest>): List<VirusScanResult>
}
