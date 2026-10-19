package no.nav.su.se.bakover.common.domain.job

/**
 * Repo for å lagre og hente kjøringsstatus for schedulerte jobber.
 * Beholder all historikk; hentSiste returnerer kun den nyeste per jobb.
 */
interface JobbKjøringRepo {
    fun lagre(jobbKjøring: JobbKjøring)
    fun oppdater(jobbKjøring: JobbKjøring)
    fun hentSistePerJobb(): List<JobbKjøring>
}
