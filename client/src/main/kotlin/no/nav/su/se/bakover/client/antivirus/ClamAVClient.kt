package no.nav.su.se.bakover.client.antivirus

interface ClamAVClient {
    fun scan(request: VirusScanRequest): ScanResponse
    fun scanBatch(requests: List<VirusScanRequest>): BatchScanResponse
}
