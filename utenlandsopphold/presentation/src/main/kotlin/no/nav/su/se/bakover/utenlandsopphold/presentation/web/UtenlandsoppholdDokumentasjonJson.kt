package no.nav.su.se.bakover.utenlandsopphold.presentation.web

import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon

enum class UtenlandsoppholdDokumentasjonJson {
    Udokumentert,
    Dokumentert,
    Sannsynliggjort,
    ;

    fun toDomain(): UtenlandsoppholdDokumentasjon {
        return when (this) {
            Udokumentert -> UtenlandsoppholdDokumentasjon.Udokumentert
            Dokumentert -> UtenlandsoppholdDokumentasjon.Dokumentert
            Sannsynliggjort -> UtenlandsoppholdDokumentasjon.Sannsynliggjort
        }
    }

    companion object {
        fun UtenlandsoppholdDokumentasjon.toJson(): UtenlandsoppholdDokumentasjonJson {
            return when (this) {
                UtenlandsoppholdDokumentasjon.Udokumentert -> Udokumentert
                UtenlandsoppholdDokumentasjon.Dokumentert -> Dokumentert
                UtenlandsoppholdDokumentasjon.Sannsynliggjort -> Sannsynliggjort
            }
        }
    }
}
