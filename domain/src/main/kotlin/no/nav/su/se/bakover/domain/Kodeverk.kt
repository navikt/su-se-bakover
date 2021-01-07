package no.nav.su.se.bakover.domain

/**
 * Samlefil for kodeverk enums, se https://kodeverk-web.nais.adeo.no/kodeverksoversikt/ for fullstendig oversikt.
 */

/**
 * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Tema/
 */
enum class Tema(val value: String) {
    SUPPLERENDE_STØNAD("SUP");

    override fun toString(): String = this.value
}

/**
 * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstyper
 * https://github.com/navikt/kodeverksmapper/blob/master/web/src/main/resources/underkategori.csv
 */
enum class Behandlingstype(val value: String) {
    FØRSTEGANGSSØKNAD("ae0245");

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
    ATTESTERING("ATT");

    override fun toString() = this.value
}
