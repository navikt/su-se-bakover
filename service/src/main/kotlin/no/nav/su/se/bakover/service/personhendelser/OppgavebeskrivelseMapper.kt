package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.domain.hendelse.Personhendelse

object OppgavebeskrivelseMapper {
    fun map(hendelse: Personhendelse.Hendelse) = when (hendelse) {
        is Personhendelse.Hendelse.Dødsfall -> {
            "Dødsfall\n" +
                "\tDødsdato: ${hendelse.dødsdato ?: "Ikke oppgitt"}"
        }
        is Personhendelse.Hendelse.Sivilstand -> {
            "Endring i sivilstand\n" +
                "\ttype: ${hendelse.type?.readableName ?: "Ikke oppgitt"}\n" +
                "\tGyldig fra og med: ${hendelse.gyldigFraOgMed ?: "Ikke oppgitt"}\n" +
                "\tBekreftelsesdato: ${hendelse.bekreftelsesdato ?: "Ikke oppgitt"}"
        }
        is Personhendelse.Hendelse.UtflyttingFraNorge -> {
            "Utflytting fra Norge\n" +
                "\tUtflyttingsdato: ${hendelse.utflyttingsdato ?: "Ikke oppgitt"}"
        }
    }
}
