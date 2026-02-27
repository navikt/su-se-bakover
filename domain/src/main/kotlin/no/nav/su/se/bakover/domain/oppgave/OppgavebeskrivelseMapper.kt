package no.nav.su.se.bakover.domain.oppgave

import arrow.core.NonEmptyCollection
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import person.domain.SivilstandTyper

data object OppgavebeskrivelseMapper {
    fun map(config: OppgaveConfig.Klage.Klageinstanshendelse): String {
        return when (config) {
            is OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall -> "Utfall: ${config.utfall.toReadableName()}" +
                "\nHendelsestype: ${config.hendelsestype}" +
                "\nRelevante JournalpostIDer: ${config.journalpostIDer.joinToString(", ")}" +
                "\nAvsluttet tidspunkt: ${config.avsluttetTidspunkt.toOppgaveFormat()}" +
                "\n\n${config.utfall.lukkBeskrivelse()}"

            is OppgaveConfig.Klage.Klageinstanshendelse.BehandlingOpprettet -> "Mottok en ny hendelse fra Kabal/KA/Klageinstansen. Hendelsestype: ${config.hendelsestype}. Deres tidspunkt: ${config.mottatt.toOppgaveFormat()}"
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

            is Personhendelse.Hendelse.Bostedsadresse -> {
                "Endring i bostedsadresse\n" +
                    personhendelse.leggtilGjelderEpsBeskrivelse() +
                    personhendelse.leggTilPdlFasitBeskrivelse() +
                    personhendelse.leggTilMetadataBeskrivelse()
            }

            is Personhendelse.Hendelse.Kontaktadresse -> {
                "Endring i kontaktadresse\n" +
                    personhendelse.leggtilGjelderEpsBeskrivelse() +
                    personhendelse.leggTilPdlFasitBeskrivelse() +
                    personhendelse.leggTilMetadataBeskrivelse()
            }
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

    private fun Personhendelse.TilknyttetSak.IkkeSendtTilOppgave.leggTilPdlFasitBeskrivelse(): String {
        val oppsummering = this.pdlOppsummering ?: return ""
        val linjer = buildList {
            oppsummering.vurdertTidspunkt?.let { add("\tPDL vurdert tidspunkt: ${it.toOppgaveFormat()}") }
            oppsummering.harBostedsadresseNå?.let { add("\tPDL har bostedsadresse nå: ${if (it) "Ja" else "Nei"}") }
            oppsummering.harKontaktadresseNå?.let { add("\tPDL har kontaktadresse nå: ${if (it) "Ja" else "Nei"}") }
            oppsummering.begrunnelse?.takeIf { it.isNotBlank() }?.let { add("\tPDL-vurdering: $it") }
        }
        return if (linjer.isEmpty()) "" else linjer.joinToString(separator = "\n", postfix = "\n")
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
        AvsluttetKlageinstansUtfall.KreverHandling.DelvisMedhold -> "Delvis medhold"
        AvsluttetKlageinstansUtfall.KreverHandling.Medhold -> "Medhold"
        AvsluttetKlageinstansUtfall.KreverHandling.Opphevet -> "Opphevet"
        AvsluttetKlageinstansUtfall.KreverHandling.Ugunst -> "Ugunst"
        AvsluttetKlageinstansUtfall.Retur -> "Retur"
        AvsluttetKlageinstansUtfall.TilInformasjon.Avvist -> "Avvist"
        AvsluttetKlageinstansUtfall.TilInformasjon.Henvist -> "Henvist"
        AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse -> "Stadfestelse"
        AvsluttetKlageinstansUtfall.TilInformasjon.Trukket -> "Trukket"
        AvsluttetKlageinstansUtfall.TilInformasjon.Henlagt -> "Henlagt"
    }

    private fun AvsluttetKlageinstansUtfall.lukkBeskrivelse() = when (this) {
        is AvsluttetKlageinstansUtfall.TilInformasjon,
        -> "Denne oppgaven er kun til opplysning og må lukkes manuelt."

        is AvsluttetKlageinstansUtfall.KreverHandling,
        -> "Klagen krever ytterligere saksbehandling. Denne oppgaven må lukkes manuelt."

        is AvsluttetKlageinstansUtfall.Retur -> "Klagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk."
    }
}
