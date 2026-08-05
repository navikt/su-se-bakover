package no.nav.su.se.bakover.client.antivirus

import no.nav.su.se.bakover.domain.antivirus.VirusScanRequest

interface ClamAVClient {
    fun scan(request: VirusScanRequest): ScanResponse
    fun scanBatch(requests: List<VirusScanRequest>): BatchScanResponse
}
