package no.nav.su.se.bakover.service.statistikk

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class Statistikk {
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

    data class Behandling(
        val funksjonellTid: String,
        val tekniskTid: String,
        val mottattDato: String,
        val registrertDato: String,
        val behandlingId: String,
        val relatertBehandlingId: String? = null,
        val sakId: String,
        val saksnummer: String,
        val behandlingType: String,
        val behandlingTypeBeskrivelse: String? = null,
        val behandlingStatus: String,
        val utenlandstilsnitt: String,
        val utenlandstilsnittBeskrivelse: String? = null,
        val ansvarligEnhetKode: String,
        val ansvarligEnhetType: String,
        val behandlendeEnhetKode: String,
        val behandlendeEnhetType: String,
        val totrinnsbehandling: Boolean,
        val avsender: String,
        val versjon: Int,
        val vedtaksDato: String? = null,
        val resultat: String? = null,
        val resultatBegrunnelse: String? = null,
        val resultatBegrunnelseBeskrivelse: String? = null,
        val resultatBeskrivelse: String? = null,
        val beslutter: String? = null,
        val saksbehandler: String? = null,
        val behandlingOpprettetAv: String? = null,
        val behandlingOpprettetType: String? = null,
        val behandlingOpprettetTypeBeskrivelse: String? = null,
        val datoForUttak: String? = null,
        val datoForUtbetaling: String? = null,
    ) : Statistikk()

    data class Aktør(val aktorId: Int, val rolle: String, val rolleBeskrivelse: String)
}
