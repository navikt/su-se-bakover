package no.nav.su.se.bakover.domain.oppgave

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.journal.JournalpostId

/**
 * https://jira.adeo.no/browse/OH-580
 */
sealed class OppgaveConfig {
    abstract val journalpostId: JournalpostId?
    abstract val sakId: String
    abstract val aktørId: AktørId
    abstract val behandlingstema: Behandlingstema?
    abstract val oppgavetype: Oppgavetype
    abstract val behandlingstype: Behandlingstype
    abstract val tilordnetRessurs: String?

    data class Saksbehandling(
        override val journalpostId: JournalpostId,
        override val sakId: String,
        override val aktørId: AktørId
    ) : OppgaveConfig() {
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val tilordnetRessurs: String? = null
    }

    data class SaksbehandlingMedTilordnetRessurs(
        override val journalpostId: JournalpostId,
        override val sakId: String,
        override val aktørId: AktørId,
        override val tilordnetRessurs: String
    ) : OppgaveConfig() {
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
    }

    data class Attestering(
        override val sakId: String,
        override val aktørId: AktørId
    ) : OppgaveConfig() {
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.TIL_ATTESTERING
        override val tilordnetRessurs: String? = null
    }

    /**
     * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstyper
     * https://github.com/navikt/kodeverksmapper/blob/master/web/src/main/resources/underkategori.csv
     */
    enum class Behandlingstype(val value: String) {
        FØRSTEGANGSSØKNAD("ae0245"),
        BEHANDLE_VEDTAK("ae0004");

        override fun toString() = this.value
    }

    /**
     * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstema
     * https://github.com/navikt/kodeverksmapper/blob/master/web/src/main/resources/underkategori.csv
     */
    enum class Behandlingstema(val value: String) {
        SU_UFØRE_FLYKNING("ab0431"),
        SU_ALDER("ab0432");

        override fun toString() = this.value
    }

    /**
     * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Oppgavetyper
     * https://github.com/navikt/kodeverksmapper/blob/master/web/src/main/resources/oppgavetype.csv
     */
    enum class Oppgavetype(val value: String) {
        BEHANDLE_SAK("BEH_SAK"),
        TIL_ATTESTERING("ATT");

        override fun toString() = this.value
    }
}
