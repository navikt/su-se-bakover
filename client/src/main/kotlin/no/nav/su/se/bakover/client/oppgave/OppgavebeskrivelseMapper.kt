package no.nav.su.se.bakover.client.oppgave

import arrow.core.NonEmptyCollection
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient.Companion.toOppgaveFormat
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import person.domain.SivilstandTyper

data object OppgavebeskrivelseMapper {
    fun map(config: OppgaveConfig.Klage.Klageinstanshendelse): String {
        return when (config) {
            is OppgaveConfig.Klage.Klageinstanshendelse.KlagebehandlingAvsluttet -> "Utfall: ${config.utfall.toReadableName()}" +
                "\nRelevante JournalpostIDer: ${config.journalpostIDer.joinToString(", ")}" +
                "\nKlageinstans sin behandling ble avsluttet den ${config.avsluttetTidspunkt.toOppgaveFormat()}" +
                "\n\n${config.utfall.lukkBeskrivelse()}"
            is OppgaveConfig.Klage.Klageinstanshendelse.AnkebehandlingOpprettet -> "Klageinstans mottok en ny anke den ${config.mottattKlageinstans.toOppgaveFormat()}"
        }
    }

    fun map(personhendelser: NonEmptyCollection<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>): String =
        personhendelser.sortedBy { it.opprettet.instant }.joinToString("\n\n") { mapOne(it) }

    fun mapOne(personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave): String =
        when (val hendelse = personhendelse.hendelse) {
            is Personhendelse.Hendelse.Dødsfall -> {
                "Dødsfall\n" +
                    personhendelse.leggtilGjelderEpsBeskrivelse() +
                    "\tDødsdato: ${hendelse.dødsdato ?: "Ikke oppgitt"}\n" +
                    personhendelse.leggTilMetadataBeskrivelse()
            }

            is Personhendelse.Hendelse.Sivilstand -> {
                "Endring i sivilstand\n" +
                    personhendelse.leggtilGjelderEpsBeskrivelse() +
                    "\tType: ${hendelse.type?.toReadableName() ?: "Ikke oppgitt"}\n" +
                    "\tGyldig fra og med: ${hendelse.gyldigFraOgMed ?: "Ikke oppgitt"}\n" +
                    "\tBekreftelsesdato: ${hendelse.bekreftelsesdato ?: "Ikke oppgitt"}\n" +
                    personhendelse.leggTilMetadataBeskrivelse()
            }

            is Personhendelse.Hendelse.UtflyttingFraNorge -> {
                "Utflytting fra Norge\n" +
                    personhendelse.leggtilGjelderEpsBeskrivelse() +
                    "\tUtflyttingsdato: ${hendelse.utflyttingsdato ?: "Ikke oppgitt"}\n" +
                    personhendelse.leggTilMetadataBeskrivelse()
            }

            is Personhendelse.Hendelse.Bostedsadresse -> "Endring i bostedsadresse\n" + personhendelse.leggtilGjelderEpsBeskrivelse() + personhendelse.leggTilMetadataBeskrivelse()
            is Personhendelse.Hendelse.Kontaktadresse -> "Endring i kontaktadresse\n" + personhendelse.leggtilGjelderEpsBeskrivelse() + personhendelse.leggTilMetadataBeskrivelse()
        }

    private fun Personhendelse.TilknyttetSak.IkkeSendtTilOppgave.leggtilGjelderEpsBeskrivelse(): String {
        return if (this.gjelderEps) {
            "\tGjelder EPS - ${
                this.metadata.personidenter.map { it }.joinToString(", ")
            }\n"
        } else {
            ""
        }
    }

    private fun Personhendelse.TilknyttetSak.IkkeSendtTilOppgave.leggTilMetadataBeskrivelse(): String {
        return "\tHendelsestidspunkt: ${(metadata.eksternOpprettet ?: opprettet).toOppgaveFormat()}\n" +
            "\tEndringstype: ${this.endringstype}\n" +
            "\tHendelseId: ${this.id}\n" +
            "\tTidligere hendelseid: ${this.metadata.tidligereHendelseId ?: "Ingen tidligere"}"
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

    private fun AvsluttetKlageinstansUtfall.toReadableName() = when (this) {
        AvsluttetKlageinstansUtfall.TRUKKET -> "Trukket"
        AvsluttetKlageinstansUtfall.RETUR -> "Retur"
        AvsluttetKlageinstansUtfall.OPPHEVET -> "Opphevet"
        AvsluttetKlageinstansUtfall.MEDHOLD -> "Medhold"
        AvsluttetKlageinstansUtfall.DELVIS_MEDHOLD -> "Delvis medhold"
        AvsluttetKlageinstansUtfall.STADFESTELSE -> "Stadfestelse"
        AvsluttetKlageinstansUtfall.UGUNST -> "Ugunst"
        AvsluttetKlageinstansUtfall.AVVIST -> "Avvist"
    }

    private fun AvsluttetKlageinstansUtfall.lukkBeskrivelse() = when (this) {
        /*
         * Informasjonsoppgaver som må lukkes manuelt.
         * */
        AvsluttetKlageinstansUtfall.STADFESTELSE,
        AvsluttetKlageinstansUtfall.TRUKKET,
        AvsluttetKlageinstansUtfall.AVVIST,
        -> "Denne oppgaven er kun til opplysning og må lukkes manuelt."
        /* Oppgaver som krever ytterligere handlinger og må lukkes manuelt. */
        AvsluttetKlageinstansUtfall.UGUNST,
        AvsluttetKlageinstansUtfall.OPPHEVET,
        AvsluttetKlageinstansUtfall.MEDHOLD,
        AvsluttetKlageinstansUtfall.DELVIS_MEDHOLD,
        -> "Klagen krever ytterligere saksbehandling. Denne oppgaven må lukkes manuelt."
        /* Oppgaver som krever ytterligere handling. Oppgaver lukkes automatisk av `su-se-bakover` */
        AvsluttetKlageinstansUtfall.RETUR -> "Klagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk."
    }
}
