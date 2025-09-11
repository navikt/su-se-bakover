package statistikk.domain

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SakStatistikk(
    val hendelseTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val sakId: UUID,
    @param:JsonSerialize(using = ToStringSerializer::class)
    val saksnummer: Long,
    val behandlingId: UUID,
    val relatertBehandlingId: UUID? = null,
    val aktorId: Fnr,

    val sakYtelse: String,
    val sakUtland: String = "NASJONAL",
    val behandlingType: String,

    // TODO kun regulering som kan være automatisk? egen håndtering
    val behandlingMetode: String = "Manuell",

    val mottattTid: Tidspunkt,
    val registrertTid: Tidspunkt,
    val ferdigbehandletTid: Tidspunkt? = null,
    val utbetaltTid: LocalDate? = null,

    val behandlingStatus: String,
    val behandlingResultat: String? = null,
    val resultatBegrunnelse: String? = null,
    val behandlingAarsak: String? = null,

    val opprettetAv: String?,
    val saksbehandler: String? = null,
    // Attestant
    val ansvarligBeslutter: String? = null,
    val ansvarligEnhet: String = "4815",

    val vedtaksløsningNavn: String = "SU-App",

    // Tilbakekreving
    val funksjonellPeriodeFom: LocalDate? = null,
    val funksjonellPeriodeTom: LocalDate? = null,
    val tilbakekrevBeløp: Long? = null,
)
