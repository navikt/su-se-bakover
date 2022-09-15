package no.nav.su.se.bakover.service.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.time.LocalDate
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class Statistikk {
    /**
     * @param funksjonellTid  Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i kildesystemet
     * @param tekniskTid  Tidspunktet da kildesystemet ble klar over hendelsen. Denne er oftest lik 'funskjonellTid'
     * @param opprettetDato  Tidspunkt da saken først blir opprettet
     * @param sakId  Nøkkelen til saken i kildesystemet
     * @param aktorId  Aktør IDen til primær mottager av ytelsen om denne blir godkjent
     * @param saksnummer  Saksnummeret tilknyttet saken
     * @param ytelseType  Stønaden eller ytelsen saken omhandler. F.eks "SUUFORE"
     * @param ytelseTypeBeskrivelse  Beskriver den funksjonelle verdien av koden. F.eks "Supplerende stønad".
     * @param sakStatus  Kode som angir sakens status. F.eks OPPRETTET.
     * @param sakStatusBeskrivelse  Beskriver den funksjonelle verdien av koden
     * @param avsender  Feltet angir hvem som er avsender av dataene. Primært su-se-bakover.
     * @param versjon  Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av
     * */
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
     * @param behandlingStatus Kode som angir den aktuelle behandlingens tilstand på gjeldende tidspunkt
     * @param behandlingYtelseDetaljer Hvorfor søkeren får en gitt sats
     * @param vedtaksDato Tidspunkt da vedtaket på behandlingen falt
     * @param vedtakId Nøkkel til det aktuelle vedtaket da behandlingen blir tilknyttet et slikt
     * @param resultat Kode som angir resultat av behandling på innværende tidspunkt
     * @param resultatBegrunnelse En årsaksbeskrivelse knyttet til et hvert mulig resultat av behandlingen
     * @param beslutter Bruker IDen til den ansvarlige beslutningstageren for saken. I vårt fall er det attestanten.
     * @param saksbehandler Bruker IDen til saksbehandler ansvarlig for saken på gjeldende tidspunkt
     * @param datoForUtbetaling Den faktiske datoen for når stønaden/ytelsen betales ut til bruker.
     * @param avsluttet Angir om behandlingen er ferdigbehandlet
     * */
    data class Behandling(
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
        val behandlingType: BehandlingType,
        val behandlingTypeBeskrivelse: String?,
        val behandlingStatus: String,
        val behandlingStatusBeskrivelse: String? = null,
        val behandlingYtelseDetaljer: List<BehandlingYtelseDetaljer>? = null,
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
    ) : Statistikk() {
        enum class BehandlingType(val beskrivelse: String) {
            SOKNAD("Søknad for SU Uføre"),
            REVURDERING("Revurdering av søknad for SU Uføre"),
            KLAGE("Klage for SU Uføre"),
        }
        enum class SøknadStatus(val beskrivelse: String) {
            SØKNAD_MOTTATT("Søknaden er mottatt"),
            SØKNAD_LUKKET("Søknaden er lukket"),
        }
    }

    /**
     * @param funksjonellTid Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i kildesystemet
     * @param tekniskTid Tidspunktet da kildesystemet ble klar over hendelsen
     * @param stonadstype Type stønad. Primært "SU Uføre"
     * @param sakId Nøkkelen til saken i kildesystemet
     * @param aktorId Aktør IDen til primær mottager av ytelsen om denne blir godkjent
     * @param sakstype Type sak
     * @param vedtaksdato Dato for når vedtaket ble fattet
     * @param vedtakstype Type vedtak, dvs. førstegangssøknad, revurdering, klage, osv
     * @param vedtaksresultat Resultatet på vedtaket, f.eks. Innvilget, Opphørt, osv
     * @param behandlendeEnhetKode Kode som angir hvilken enhet som faktisk utfører behandlingen på det gjeldende tidspunktet
     * @param ytelseVirkningstidspunkt Dato for når stønadsmottakers ytelse trådte i kraft første gang
     * @param gjeldendeStonadVirkningstidspunkt Dato for når gjeldende stønadsperiode startes
     * @param gjeldendeStonadStopptidspunkt Dato for når gjeldende stønadsperiode avsluttes
     * @param gjeldendeStonadUtbetalingsstart Dato for når utbetalingene starter for gjeldende stønadsperiode
     * @param gjeldendeStonadUtbetalingsstopp Dato for når utbetalingene stoppes for gjeldende stønadsperiode
     * @param månedsbeløp Liste over utbetalingsinformasjonen for hver enkelt måned
     * @param versjon Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av
     * @param opphorsgrunn Grunn for opphør av ytelsen
     * @param opphorsdato Dato opphøret trer i kraft
     * @param flyktningsstatus Hvorvidt stønadsmottaker har status som flyktning
     */
    data class Stønad(
        val funksjonellTid: Tidspunkt,
        val tekniskTid: Tidspunkt,
        val stonadstype: Stønadstype,
        val sakId: UUID,
        val aktorId: Long,
        val sakstype: Vedtakstype, // TODO: Skulle denne være noe annet enn en duplikat av vedtakstype?
        val vedtaksdato: LocalDate,
        val vedtakstype: Vedtakstype,
        val vedtaksresultat: Vedtaksresultat,
        val behandlendeEnhetKode: String,
        val ytelseVirkningstidspunkt: LocalDate,
        val gjeldendeStonadVirkningstidspunkt: LocalDate,
        val gjeldendeStonadStopptidspunkt: LocalDate,
        val gjeldendeStonadUtbetalingsstart: LocalDate,
        val gjeldendeStonadUtbetalingsstopp: LocalDate,
        val månedsbeløp: List<Månedsbeløp>,
        val versjon: Long,
        val opphorsgrunn: String? = null,
        val opphorsdato: LocalDate? = null,
        val flyktningsstatus: String? = "FLYKTNING", // Alle som gjelder SU Ufør vil være flyktning
    ) : Statistikk() {
        enum class Stønadstype(val beskrivelse: String) {
            SU_UFØR("SU Ufør"),
        }
        enum class Vedtakstype(val beskrivelse: String) {
            SØKNAD("Søknad"),
            REVURDERING("Revurdering"),
            STANS("Stans"),
            GJENOPPTAK("Gjenopptak"),
            REGULERING("Regulering"),
        }
        enum class Vedtaksresultat(val beskrivelse: String) {
            INNVILGET("Innvilget"),
            OPPHØRT("Opphørt"),
            STANSET("Stanset"),
            GJENOPPTATT("Gjenopptatt"),
            REGULERT("Regulert"),
        }

        data class Månedsbeløp(
            val måned: String,
            val stonadsklassifisering: Stønadsklassifisering,
            val bruttosats: Long,
            val nettosats: Long,
            val inntekter: List<Inntekt>,
            val fradragSum: Long,
        )
    }

    data class Inntekt(
        val inntektstype: String,
        val beløp: Long,
        val tilhører: String,
        val erUtenlandsk: Boolean,
    )

    data class Aktør(val aktorId: Int, val rolle: String, val rolleBeskrivelse: String)

    data class BehandlingYtelseDetaljer(
        val satsgrunn: Stønadsklassifisering,
    )

    enum class Stønadsklassifisering(val beskrivelse: String) {
        BOR_ALENE("Bor alene"),
        BOR_MED_ANDRE_VOKSNE("Bor med andre voksne"),
        BOR_MED_EKTEFELLE_UNDER_67_IKKE_UFØR_FLYKTNING("Bor med ektefelle under 67 år, ikke ufør flyktning"),
        BOR_MED_EKTEFELLE_OVER_67("Bor med ektefelle over 67 år"),
        BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING("Bor med ektefelle under 67 år, ufør flyktning"),
    }
}

internal fun Grunnlag.Bosituasjon.Fullstendig.stønadsklassifisering(): Statistikk.Stønadsklassifisering {
    return when (this) {
        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Statistikk.Stønadsklassifisering.BOR_MED_ANDRE_VOKSNE
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Statistikk.Stønadsklassifisering.BOR_MED_EKTEFELLE_UNDER_67_IKKE_UFØR_FLYKTNING
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> Statistikk.Stønadsklassifisering.BOR_MED_EKTEFELLE_OVER_67
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> Statistikk.Stønadsklassifisering.BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING
        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> Statistikk.Stønadsklassifisering.BOR_ALENE
    }
}
