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
        val aktorer: List<Aktør>?,
        val underType: String?,
        val ytelseTypeBeskrivelse: String?,
        val underTypeBeskrivelse: String?,
        val sakStatusBeskrivelse: String?,
    ) : Statistikk()

    data class Aktør(val aktorId: Int, val rolle: String, val rolleBeskrivelse: String)
}
