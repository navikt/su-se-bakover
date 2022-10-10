package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon

enum class UtenlandsoppholdDokumentasjonDatabaseJson {
    Udokumentert,
    Dokumentert,
    Sannsynliggjort,
    ;

    override fun toString() = this.name

    fun toDomain(): UtenlandsoppholdDokumentasjon = when (this) {
        UtenlandsoppholdDokumentasjonDatabaseJson.Udokumentert -> UtenlandsoppholdDokumentasjon.Udokumentert
        UtenlandsoppholdDokumentasjonDatabaseJson.Dokumentert -> UtenlandsoppholdDokumentasjon.Dokumentert
        UtenlandsoppholdDokumentasjonDatabaseJson.Sannsynliggjort -> UtenlandsoppholdDokumentasjon.Sannsynliggjort
    }

    companion object {
        fun UtenlandsoppholdDokumentasjon.toJson(): UtenlandsoppholdDokumentasjonDatabaseJson = when (this) {
            UtenlandsoppholdDokumentasjon.Udokumentert -> UtenlandsoppholdDokumentasjonDatabaseJson.Udokumentert
            UtenlandsoppholdDokumentasjon.Dokumentert -> UtenlandsoppholdDokumentasjonDatabaseJson.Dokumentert
            UtenlandsoppholdDokumentasjon.Sannsynliggjort -> UtenlandsoppholdDokumentasjonDatabaseJson.Sannsynliggjort
        }
    }
}
