package no.nav.su.se.bakover.client.oppgave

import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.person.SivilstandTyper

object OppgavebeskrivelseMapper {
    fun map(hendelse: Personhendelse.Hendelse) = when (hendelse) {
        is Personhendelse.Hendelse.Dødsfall -> {
            "Dødsfall\n" +
                "\tDødsdato: ${hendelse.dødsdato ?: "Ikke oppgitt"}"
        }
        is Personhendelse.Hendelse.Sivilstand -> {
            "Endring i sivilstand\n" +
                "\ttype: ${hendelse.type?.toReadableName() ?: "Ikke oppgitt"}\n" +
                "\tGyldig fra og med: ${hendelse.gyldigFraOgMed ?: "Ikke oppgitt"}\n" +
                "\tBekreftelsesdato: ${hendelse.bekreftelsesdato ?: "Ikke oppgitt"}"
        }
        is Personhendelse.Hendelse.UtflyttingFraNorge -> {
            "Utflytting fra Norge\n" +
                "\tUtflyttingsdato: ${hendelse.utflyttingsdato ?: "Ikke oppgitt"}"
        }
    }

    private fun SivilstandTyper.toReadableName() = when (this) {
        SivilstandTyper.UOPPGITT -> "Uppgitt"
        SivilstandTyper.UGIFT -> "Ugift"
        SivilstandTyper.GIFT -> "Gift"
        SivilstandTyper.ENKE_ELLER_ENKEMANN -> "Enke eller enkemann"
        SivilstandTyper.SKILT -> "Skilt"
        SivilstandTyper.SEPARERT -> "Separert"
        SivilstandTyper.REGISTRERT_PARTNER -> "Registrert partner"
        SivilstandTyper.SEPARERT_PARTNER -> "Separert partner"
        SivilstandTyper.SKILT_PARTNER -> "Skilt partner"
        SivilstandTyper.GJENLEVENDE_PARTNER -> "Gjenlevende partner"
    }
}
