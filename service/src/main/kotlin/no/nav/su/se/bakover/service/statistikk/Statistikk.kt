package no.nav.su.se.bakover.service.statistikk

import com.fasterxml.jackson.annotation.JsonInclude

sealed class Statistikk {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Sak(
        val funksjonellTid: String,
        val tekniskTid: String,
        val opprettetDato: String,
        val sakId: String,
        val aktorId: Int,
        val saksnummer: String,
        val ytelseType: String,
        val sakStatus: String,
        val avsender: String,
        val versjon: Int,
        val aktorer: List<Aktør>? = null,
        val underType: String? = null,
        val ytelseTypeBeskrivelse: String? = null,
        val underTypeBeskrivelse: String? = null,
        val sakStatusBeskrivelse: String? = null,
    ) : Statistikk()

    data class Aktør(val aktorId: Int, val rolle: String, val rolleBeskrivelse: String)
}
