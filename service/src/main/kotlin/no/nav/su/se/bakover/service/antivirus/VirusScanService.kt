package no.nav.su.se.bakover.service.antivirus

import no.nav.su.se.bakover.client.antivirus.BatchScanResponse
import no.nav.su.se.bakover.client.antivirus.ScanResponse
import no.nav.su.se.bakover.client.antivirus.VirusScanRequest

interface VirusScanService {
    fun scan(request: VirusScanRequest): ScanResponse
    fun scanBatch(requests: List<VirusScanRequest>): BatchScanResponse
}
