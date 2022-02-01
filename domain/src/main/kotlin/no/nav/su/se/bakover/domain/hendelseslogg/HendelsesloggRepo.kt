package no.nav.su.se.bakover.domain.hendelseslogg

interface HendelsesloggRepo {
    fun hentHendelseslogg(id: String): Hendelseslogg?
    fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg)
}
