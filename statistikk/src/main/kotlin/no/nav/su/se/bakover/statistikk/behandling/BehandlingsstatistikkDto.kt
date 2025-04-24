package no.nav.su.se.bakover.statistikk.behandling

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import java.time.LocalDate
import java.util.UUID

/**
 * Se `src/resources/behandling_schema.json` for dokumentasjon.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class BehandlingsstatistikkDto(
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val mottattDato: LocalDate,
    val registrertDato: LocalDate,
    val behandlingId: UUID?,
    val relatertBehandlingId: UUID? = null,
    val sakId: UUID,
    val søknadId: UUID? = null,
    @param:JsonSerialize(using = ToStringSerializer::class)
    val saksnummer: Long,
    val behandlingType: Behandlingstype,
    val behandlingTypeBeskrivelse: String?,
    val behandlingStatus: String,
    val behandlingStatusBeskrivelse: String? = null,
    val behandlingYtelseDetaljer: List<BehandlingYtelseDetaljer>? = emptyList(),
    val utenlandstilsnitt: String = "NASJONAL",
    val utenlandstilsnittBeskrivelse: String? = null,
    val ansvarligEnhetKode: String = "4815",
    val ansvarligEnhetType: String = "NORG",
    val behandlendeEnhetKode: String = "4815",
    val behandlendeEnhetType: String = "NORG",
    val totrinnsbehandling: Boolean = true,
    val avsender: String = "su-se-bakover",
    val versjon: String?,
    val vedtaksDato: LocalDate? = null,
    val vedtakId: UUID? = null,
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
    val avsluttet: Boolean,
) {

    data class BehandlingYtelseDetaljer(
        val satsgrunn: StønadsklassifiseringDto,
    )
}
