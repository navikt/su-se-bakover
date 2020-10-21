package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold.Companion.lagTrukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.brev.Brevinnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class LukketSøknadBrevinnhold : Brevinnhold() {
    abstract val dato: String
    abstract val datoSøknadOpprettet: String
    abstract val fødselsnummer: Fnr
    abstract val fornavn: String
    abstract val mellomnavn: String?
    abstract val etternavn: String
    abstract val adresse: String?
    abstract val husnummer: String?
    abstract val bruksenhet: String?
    abstract val postnummer: String?
    abstract val poststed: String?

    data class TrukketSøknadBrevinnhold(
        override val dato: String,
        override val datoSøknadOpprettet: String,
        val datoSøkerTrakkSøknad: String,
        override val fødselsnummer: Fnr,
        override val fornavn: String,
        override val mellomnavn: String?,
        override val etternavn: String,
        override val adresse: String?,
        override val husnummer: String?,
        override val bruksenhet: String?,
        override val postnummer: String?,
        override val poststed: String?,
    ) : LukketSøknadBrevinnhold() {

        companion object {
            fun lagTrukketSøknadBrevinnhold(
                person: Person,
                søknad: Søknad,
                lukketSøknad: Søknad.Lukket.Trukket
            ): TrukketSøknadBrevinnhold {
                return TrukketSøknadBrevinnhold(
                    dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    datoSøknadOpprettet = LocalDate.ofInstant(søknad.opprettet.instant, ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    datoSøkerTrakkSøknad = lukketSøknad.datoSøkerTrakkSøknad.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    fødselsnummer = person.ident.fnr,
                    fornavn = person.navn.fornavn,
                    mellomnavn = person.navn.mellomnavn,
                    etternavn = person.navn.etternavn,
                    adresse = person.adresse?.adressenavn,
                    bruksenhet = person.adresse?.bruksenhet,
                    husnummer = person.adresse?.husnummer,
                    postnummer = person.adresse?.poststed?.postnummer,
                    poststed = person.adresse?.poststed?.poststed,
                )
            }
        }

        override fun toJson() = objectMapper.writeValueAsString(this)
        override fun pdfTemplate(): PdfTemplate = PdfTemplate.TrukketSøknad
    }

    companion object {
        fun lagLukketSøknadBrevinnhold(
            person: Person,
            søknad: Søknad,
            lukketSøknad: Søknad.Lukket
        ): LukketSøknadBrevinnhold =
            when (lukketSøknad) {
                is Søknad.Lukket.Trukket ->
                    lagTrukketSøknadBrevinnhold(
                        person = person, søknad = søknad, lukketSøknad = lukketSøknad
                    )
            }
    }
}
