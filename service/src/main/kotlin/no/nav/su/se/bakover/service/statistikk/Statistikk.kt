package no.nav.su.se.bakover.service.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus
import java.time.LocalDate
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class Statistikk {
    data class Sak(
        val funksjonellTid: Tidspunkt,
        val tekniskTid: Tidspunkt,
        val opprettetDato: LocalDate,
        val sakId: UUID,
        val aktorId: Long,
        @JsonSerialize(using = ToStringSerializer::class)
        val saksnummer: Long,
        val ytelseType: String = "SUUFORE",
        val ytelseTypeBeskrivelse: String? = "Supplerende stønad for uføre flyktninger",
        val sakStatus: String = "OPPRETTET",
        val sakStatusBeskrivelse: String? = null,
        val avsender: String = "su-se-bakover",
        val versjon: Long = System.currentTimeMillis(),
        val aktorer: List<Aktør>? = null,
        val underType: String? = null,
        val underTypeBeskrivelse: String? = null,
    ) : Statistikk()

    data class Behandling(
        val funksjonellTid: Tidspunkt,
        val tekniskTid: Tidspunkt,
        val mottattDato: LocalDate,
        val registrertDato: LocalDate,
        val behandlingId: UUID,
        val relatertBehandlingId: UUID? = null,
        val sakId: UUID,
        @JsonSerialize(using = ToStringSerializer::class)
        val saksnummer: Long,
        val behandlingType: String = "SOKNAD",
        val behandlingTypeBeskrivelse: String? = "Søknad for SU Uføre",
        val behandlingStatus: BehandlingsStatus,
        val utenlandstilsnitt: String = "NASJONAL",
        val utenlandstilsnittBeskrivelse: String? = null,
        val ansvarligEnhetKode: String = "4815",
        val ansvarligEnhetType: String = "NORG",
        val behandlendeEnhetKode: String = "4815",
        val behandlendeEnhetType: String = "NORG",
        val totrinnsbehandling: Boolean = true,
        val avsender: String = "su-se-bakover",
        val versjon: Long = System.currentTimeMillis(),
        val vedtaksDato: LocalDate? = null,
        val resultat: LocalDate? = null,
        val resultatBegrunnelse: String? = null,
        val resultatBegrunnelseBeskrivelse: String? = null,
        val resultatBeskrivelse: String? = null,
        val beslutter: NavIdentBruker.Attestant? = null,
        val saksbehandler: NavIdentBruker.Saksbehandler? = null,
        val behandlingOpprettetAv: String? = null,
        val behandlingOpprettetType: String? = null,
        val behandlingOpprettetTypeBeskrivelse: String? = null,
        val datoForUttak: String? = null,
        val datoForUtbetaling: String? = null,
    ) : Statistikk()

    data class Aktør(val aktorId: Int, val rolle: String, val rolleBeskrivelse: String)
}
