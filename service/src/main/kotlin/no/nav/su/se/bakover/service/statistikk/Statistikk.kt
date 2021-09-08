package no.nav.su.se.bakover.service.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import no.nav.su.se.bakover.common.Tidspunkt
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
            REVURDERING("Revurdering av søknad for SU Uføre")
        }
        enum class SøknadStatus(val beskrivelse: String) {
            SØKNAD_MOTTATT("Søknaden er mottatt"),
            SØKNAD_LUKKET("Søknaden er lukket")
        }
    }

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
        val versjon: Long = System.currentTimeMillis(),
        val opphorsgrunn: String? = null,
        val opphorsdato: LocalDate? = null,
        val flyktningsstatus: String? = "Flyktning" // Alle som gjelder SU Ufør vil være flyktning
    ) : Statistikk() {
        enum class Stønadstype(val beskrivelse: String) {
            SU_UFØR("SU Ufør"),
        }
        enum class Vedtakstype(val beskrivelse: String) {
            SØKNAD("Søknad"),
            REVURDERING("Revurdering"),
        }
        enum class Vedtaksresultat(val beskrivelse: String) {
            INNVILGET("Innvilget"),
            OPPHØRT("Opphørt"),
        }
        enum class Stønadsklassifisering(val beskrivelse: String) {
            BOR_ALENE("Bor alene"),
            BOR_MED_ANDRE_VOKSNE("Bor med andre voksne"),
            BOR_MED_EKTEFELLE_UNDER_67_IKKE_UFØR_FLYKTNING("Bor med ektefelle under 67 år, ikke ufør flyktning"),
            BOR_MED_EKTEFELLE_OVER_67("Bor med ektefelle over 67 år"),
            BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING("Bor med ektefelle under 67 år, ufør flyktning"),
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
    )

    data class Aktør(val aktorId: Int, val rolle: String, val rolleBeskrivelse: String)
}
