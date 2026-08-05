package no.nav.su.se.bakover.domain.antivirus

class VirusScanServiceMock : VirusScanService {
    override fun scan(request: VirusScanRequest) {
        // Do nothing
    }

    override fun scanBatch(requests: List<VirusScanRequest>): List<VirusScanResult> {
        return requests.map { request ->
            VirusScanResult(
                filename = request.tittel,
                status = VirusScanResult.Status.OK,
            )
        }
    }
}
