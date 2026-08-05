package no.nav.su.se.bakover.domain.antivirus

interface VirusScanService {
    /**
     * Denne kaster om requesten ikke går ok, så den er bare å kalle også håndterer den resten selv.
     */
    fun scan(request: VirusScanRequest)

    /**
     * Obs her må man sjekke resultatene for å avgjøre hva man gjør videre
     */
    fun scanBatch(requests: List<VirusScanRequest>): List<VirusScanResult>
}
