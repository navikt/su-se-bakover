package no.nav.su.se.bakover.database.hendelseslogg

import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg

interface HendelsesloggRepo {
    fun hentHendelseslogg(id: String): Hendelseslogg?
}
