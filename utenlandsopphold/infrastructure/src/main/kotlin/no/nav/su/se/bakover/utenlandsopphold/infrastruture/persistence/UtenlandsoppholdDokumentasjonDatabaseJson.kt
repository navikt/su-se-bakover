package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon

enum class UtenlandsoppholdDokumentasjonDatabaseJson {
    Udokumentert,
    Dokumentert,
    Sannsynliggjort,
    ;

    override fun toString() = this.name

    fun toDomain(): UtenlandsoppholdDokumentasjon = when (this) {
        Udokumentert -> UtenlandsoppholdDokumentasjon.Udokumentert
        Dokumentert -> UtenlandsoppholdDokumentasjon.Dokumentert
        Sannsynliggjort -> UtenlandsoppholdDokumentasjon.Sannsynliggjort
    }

    companion object {
        fun UtenlandsoppholdDokumentasjon.toJson(): UtenlandsoppholdDokumentasjonDatabaseJson = when (this) {
            UtenlandsoppholdDokumentasjon.Udokumentert -> Udokumentert
            UtenlandsoppholdDokumentasjon.Dokumentert -> Dokumentert
            UtenlandsoppholdDokumentasjon.Sannsynliggjort -> Sannsynliggjort
        }
    }
}
