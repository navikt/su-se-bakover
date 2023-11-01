package no.nav.su.se.bakover.oppgave.domain

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

    companion object {
        fun fromString(type: String): Oppgavetype = when (type) {
            "BEH_SAK" -> BEHANDLE_SAK
            "ATT" -> ATTESTERING
            "FREM" -> FREMLEGGING
            "VUR_KONS_YTE" -> VURDER_KONSEKVENS_FOR_YTELSE
            else -> throw IllegalStateException("Ukjent oppgavetype - fikk $type")
        }
    }
}
