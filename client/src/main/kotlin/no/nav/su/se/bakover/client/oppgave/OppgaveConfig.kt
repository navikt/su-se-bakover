package no.nav.su.se.bakover.client.oppgave

import no.nav.su.se.bakover.domain.AktørId

private const val TEMA_SU_UFØR_FLYKTNING = "ab0431"
private const val TEMA_SU_UFØR_ALDER = "ab0432"

/**
 * https://jira.adeo.no/browse/OH-580
 */
sealed class OppgaveConfig() {
    abstract val journalpostId: String
    abstract val sakId: String
    abstract val aktørId: AktørId
    abstract val behandlingstema: Behandlingstema
    abstract val oppgavetype: Oppgavetype
    abstract val behandlingstype: Behandlingstype

    data class Saksbehandling(
        override val journalpostId: String,
        override val sakId: String,
        override val aktørId: AktørId
    ) : OppgaveConfig() {
        override val behandlingstema = Behandlingstema.FLYKTNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
    }

    data class Attestering(
        override val journalpostId: String,
        override val sakId: String,
        override val aktørId: AktørId
    ) : OppgaveConfig() {
        override val behandlingstema = Behandlingstema.FLYKTNING
        override val behandlingstype = Behandlingstype.BEHANDLE_VEDTAK
        override val oppgavetype = Oppgavetype.TIL_ATTESTERING
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
        FLYKTNING("ab0431"),
        ALDER("ab0432");

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
