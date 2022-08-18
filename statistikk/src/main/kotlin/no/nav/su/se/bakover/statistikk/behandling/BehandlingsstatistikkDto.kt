package no.nav.su.se.bakover.statistikk.behandling

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import java.time.LocalDate
import java.util.UUID

/**
 * @param funksjonellTid Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i kildesystemet
 * @param tekniskTid Tidspunktet da kildesystemet ble klar over hendelsen
 * @param mottattDato Denne datoen forteller fra hvilken dato behandlingen først ble initiert
 * @param registrertDato Tidspunkt for når behandlingen ble registrert i saksbehandlingssystemet. Denne kan avvike fra mottattDato f.eks ved papirsøknad.
 * @param behandlingId Nøkkel til den aktuelle behandling, som kan identifiserer den i kildensystemet
 * @param relatertBehandlingId Hvis behandlingen oppstår som resultat av en tidligere behandling, skal det refereres til denne behandlingen. F.eks ved Revurdering.
 * @param sakId Nøkkelen til saken i kildesystemet
 * @param søknadId Id på søknaden som behandlingen er knyttet til
 * @param saksnummer Saksnummeret tilknyttet saken
 * @param behandlingType Kode som beskriver behandlingen, for eksempel, søknad, revurdering, klage etc.
 * @param behandlingTypeBeskrivelse
 * @param behandlingStatus Kode som angir den aktuelle behandlingens tilstand på gjeldende tidspunkt
 * @param behandlingStatusBeskrivelse
 * @param behandlingYtelseDetaljer Hvorfor søkeren får en gitt sats
 * @param utenlandstilsnitt
 * @param utenlandstilsnittBeskrivelse
 * @param vedtaksDato Tidspunkt da vedtaket på behandlingen falt
 * @param vedtakId Nøkkel til det aktuelle vedtaket da behandlingen blir tilknyttet et slikt
 * @param resultat Kode som angir resultat av behandling på innværende tidspunkt
 * @param resultatBegrunnelse En årsaksbeskrivelse knyttet til et hvert mulig resultat av behandlingen
 * @param beslutter Bruker IDen til den ansvarlige beslutningstageren for saken. I vårt fall er det attestanten.
 * @param saksbehandler Bruker IDen til saksbehandler ansvarlig for saken på gjeldende tidspunkt
 * @param datoForUtbetaling Den faktiske datoen for når stønaden/ytelsen betales ut til bruker.
 * @param avsluttet Angir om behandlingen er ferdigbehandlet
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
    @JsonSerialize(using = ToStringSerializer::class)
    val saksnummer: Long,
    val behandlingType: Behandlingstype,
    val behandlingTypeBeskrivelse: String?,
    val behandlingStatus: BehandlingStatus,
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
    val resultat: BehandlingResultat? = null,
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
