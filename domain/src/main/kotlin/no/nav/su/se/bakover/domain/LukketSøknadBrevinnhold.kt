package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold.Companion.lagTrukketSøknadBrevinnhold
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class LukketSøknadBrevinnhold {
    abstract val dato: String
    abstract val fødselsnummer: Fnr
    abstract val fornavn: String
    abstract val etternavn: String
    abstract val adresse: String?
    abstract val husnummer: String?
    abstract val bruksenhet: String?
    abstract val postnummer: String?
    abstract val poststed: String

    data class TrukketSøknadBrevinnhold(
        override val dato: String,
        override val fødselsnummer: Fnr,
        override val fornavn: String,
        override val etternavn: String,
        override val adresse: String?,
        override val husnummer: String?,
        override val bruksenhet: String?,
        override val postnummer: String?,
        override val poststed: String
    ) : LukketSøknadBrevinnhold() {
        companion object {
            fun lagTrukketSøknadBrevinnhold(person: Person): TrukketSøknadBrevinnhold {
                return TrukketSøknadBrevinnhold(
                    dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    fødselsnummer = person.ident.fnr,
                    fornavn = person.navn.fornavn,
                    etternavn = person.navn.etternavn,
                    adresse = person.adresse?.adressenavn,
                    bruksenhet = person.adresse?.bruksenhet,
                    husnummer = person.adresse?.husnummer,
                    postnummer = person.adresse?.poststed?.postnummer,
                    poststed = person.adresse?.poststed?.poststed!!,
                )
            }

            fun buildTestData(): TrukketSøknadBrevinnhold {
                return TrukketSøknadBrevinnhold(
                    dato = "01.01.2020",
                    fødselsnummer = Fnr("12345678901"),
                    fornavn = "Tore",
                    etternavn = "Strømøy",
                    adresse = "en Adresse",
                    husnummer = "4C",
                    bruksenhet = "H102",
                    postnummer = "0186",
                    poststed = "Oslo",
                )
            }
        }
    }

    companion object {
        fun lagLukketSøknadBrevinnhold(
            person: Person,
            typeLukking: Søknad.TypeLukking
        ): LukketSøknadBrevinnhold =
            when {
                erTrukket(typeLukking) -> lagTrukketSøknadBrevinnhold(person)
                else -> throw java.lang.RuntimeException(
                    "Kan ikke lage brevinnhold for å lukke søknad som ikke er trukket eller avvist"
                )
            }

        private fun erTrukket(typeLukking: Søknad.TypeLukking): Boolean {
            return typeLukking == Søknad.TypeLukking.Trukket
        }
    }
}
