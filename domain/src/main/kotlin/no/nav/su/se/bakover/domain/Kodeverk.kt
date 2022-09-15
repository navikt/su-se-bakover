package no.nav.su.se.bakover.domain

/**
 * Samlefil for kodeverk enums, se https://kodeverk-web.nais.adeo.no/kodeverksoversikt/ for fullstendig oversikt.
 */

/**
 * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Tema/
 */
enum class Tema(val value: String) {
    SUPPLERENDE_STØNAD("SUP"),
    ;

    override fun toString(): String = this.value
}

/**
 * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstyper
 * https://github.com/navikt/kodeverksmapper/blob/master/web/src/main/resources/underkategori.csv
 */
enum class Behandlingstype(val value: String) {
    /** UFOR_FLYKT_SOK_SUP;ab0431;ae0034;SUP */
    SØKNAD("ae0034"),

    /** UFOR_FLYKT_REVUR_SUP;ab0431;ae0028;SUP */
    REVURDERING("ae0028"),

    /** UFOR_FLYKT_KLAGE_SUP;ab0431;ae0058;SUP */
    KLAGE("ae0058"),
    ;

    override fun toString() = this.value
}

/**
 * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstema
 * https://github.com/navikt/kodeverksmapper/blob/master/web/src/main/resources/underkategori.csv
 */
enum class Behandlingstema(val value: String) {
    SU_UFØRE_FLYKTNING("ab0431"),
    SU_ALDER("ab0432"),
    ;

    override fun toString() = this.value
}

/**
 * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Oppgavetyper
 * https://github.com/navikt/kodeverksmapper/blob/master/web/src/main/resources/oppgavetype.csv
 */
enum class Oppgavetype(val value: String) {
    BEHANDLE_SAK("BEH_SAK"),
    ATTESTERING("ATT"),
    FREMLEGGING("FREM"),
    VURDER_KONSEKVENS_FOR_YTELSE("VUR_KONS_YTE"),
    ;

    override fun toString() = this.value
}
