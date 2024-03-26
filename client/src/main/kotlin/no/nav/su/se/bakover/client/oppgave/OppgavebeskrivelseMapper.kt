package no.nav.su.se.bakover.client.oppgave

import arrow.core.NonEmptyCollection
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient.Companion.toOppgaveFormat
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import person.domain.SivilstandTyper

data object OppgavebeskrivelseMapper {
    fun map(config: OppgaveConfig.Klage.Klageinstanshendelse): String {
        return "Utfall: ${config.utfall.toReadableName()}" +
            "\nRelevante JournalpostIDer: ${config.journalpostIDer.joinToString(", ")}" +
            "\nKlageinstans sin behandling ble avsluttet den ${config.avsluttetTidspunkt.toOppgaveFormat()}" +
            "\n\n${config.utfall.lukkBeskrivelse()}"
    }

    fun map(personhendelser: NonEmptyCollection<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>): String =
        personhendelser
            .sortedBy { it.opprettet.instant }
            .map { mapOne(it) }
            .joinToString { "\n\n" }

    fun mapOne(personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave): String =
        when (val hendelse = personhendelse.hendelse) {
            is Personhendelse.Hendelse.Dødsfall -> {
                "Dødsfall\n" +
                    "\tDødsdato: ${hendelse.dødsdato ?: "Ikke oppgitt"}\n" +
                    personhendelse.leggTilMetadataBeskrivelse()
            }

            is Personhendelse.Hendelse.Sivilstand -> {
                "Endring i sivilstand\n" +
                    "\tType: ${hendelse.type?.toReadableName() ?: "Ikke oppgitt"}\n" +
                    "\tGyldig fra og med: ${hendelse.gyldigFraOgMed ?: "Ikke oppgitt"}\n" +
                    "\tBekreftelsesdato: ${hendelse.bekreftelsesdato ?: "Ikke oppgitt"}\n" +
                    personhendelse.leggTilMetadataBeskrivelse()
            }

            is Personhendelse.Hendelse.UtflyttingFraNorge -> {
                "Utflytting fra Norge\n" +
                    "\tUtflyttingsdato: ${hendelse.utflyttingsdato ?: "Ikke oppgitt"}\n" +
                    personhendelse.leggTilMetadataBeskrivelse()
            }

            is Personhendelse.Hendelse.Bostedsadresse -> "Endring i bostedsadresse\n" + personhendelse.leggTilMetadataBeskrivelse()
            is Personhendelse.Hendelse.Kontaktadresse -> "Endring i kontaktadresse\n" + personhendelse.leggTilMetadataBeskrivelse()
        }

    private fun Personhendelse.TilknyttetSak.IkkeSendtTilOppgave.leggTilMetadataBeskrivelse(): String {
        return "\tHendelsestidspunkt: ${(metadata.eksternOpprettet ?: opprettet).toOppgaveFormat()}\n" +
            "\tEndringstype: ${this.endringstype}\n" +
            "\tHendelseId: ${this.id}\n" +
            "\tTidligere hendelseid: ${this.metadata.tidligereHendelseId ?: "Ingen tidligere"}" +
            if (this.gjelderEps) "\n\tGjelder EPS - ${this.metadata.personidenter.map { it }.joinToString(", ")}" else ""
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

    private fun KlageinstansUtfall.toReadableName() = when (this) {
        KlageinstansUtfall.TRUKKET -> "Trukket"
        KlageinstansUtfall.RETUR -> "Retur"
        KlageinstansUtfall.OPPHEVET -> "Opphevet"
        KlageinstansUtfall.MEDHOLD -> "Medhold"
        KlageinstansUtfall.DELVIS_MEDHOLD -> "Delvis medhold"
        KlageinstansUtfall.STADFESTELSE -> "Stadfestelse"
        KlageinstansUtfall.UGUNST -> "Ugunst"
        KlageinstansUtfall.AVVIST -> "Avvist"
    }

    private fun KlageinstansUtfall.lukkBeskrivelse() = when (this) {
        /*
         * Informasjonsoppgaver som må lukkes manuelt.
         * */
        KlageinstansUtfall.STADFESTELSE,
        KlageinstansUtfall.TRUKKET,
        KlageinstansUtfall.AVVIST,
        -> "Denne oppgaven er kun til opplysning og må lukkes manuelt."
        /* Oppgaver som krever ytterligere handlinger og må lukkes manuelt. */
        KlageinstansUtfall.UGUNST,
        KlageinstansUtfall.OPPHEVET,
        KlageinstansUtfall.MEDHOLD,
        KlageinstansUtfall.DELVIS_MEDHOLD,
        -> "Klagen krever ytterligere saksbehandling. Denne oppgaven må lukkes manuelt."
        /* Oppgaver som krever ytterligere handling. Oppgaver lukkes automatisk av `su-se-bakover` */
        KlageinstansUtfall.RETUR -> "Klagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk."
    }
}
