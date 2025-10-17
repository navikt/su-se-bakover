package statistikk.domain

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import statistikk.domain.StønadstatistikkDto.Stønadstype
import statistikk.domain.StønadstatistikkDto.Vedtaksresultat
import statistikk.domain.StønadstatistikkDto.Vedtakstype
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StønadstatistikkMåned(
    val id: UUID,
    val måned: YearMonth,
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val sakId: UUID,
    val stonadstype: Stønadstype,
    val personnummer: Fnr,
    val personNummerEps: Fnr? = null,
    val vedtaksdato: LocalDate,
    val vedtakstype: Vedtakstype,
    val vedtaksresultat: Vedtaksresultat,
    val vedtakFraOgMed: LocalDate,
    val vedtakTilOgMed: LocalDate,
    val opphorsgrunn: String? = null,
    val opphorsdato: LocalDate? = null,

    val årsakStans: String? = null,

    val behandlendeEnhetKode: String,

    val stonadsklassifisering: StønadsklassifiseringDto?,
    val sats: Long?,
    val utbetales: Long?,
    val fradragSum: Long?,
    val uføregrad: Int?,
    val fribeløpEps: Long?,

    val alderspensjon: Int?,
    val alderspensjonEps: Int?,

    val arbeidsavklaringspenger: Int?,
    val arbeidsavklaringspengerEps: Int?,

    val arbeidsinntekt: Int?,
    val arbeidsinntektEps: Int?,

    val omstillingsstønad: Int?,
    val omstillingsstønadEps: Int?,

    val avtalefestetPensjon: Int?,
    val avtalefestetPensjonEps: Int?,

    val avtalefestetPensjonPrivat: Int?,
    val avtalefestetPensjonPrivatEps: Int?,

    val bidragEtterEkteskapsloven: Int?,
    val bidragEtterEkteskapslovenEps: Int?,

    val dagpenger: Int?,
    val dagpengerEps: Int?,

    val fosterhjemsgodtgjørelse: Int?,
    val fosterhjemsgodtgjørelseEps: Int?,

    val gjenlevendepensjon: Int?,
    val gjenlevendepensjonEps: Int?,

    val introduksjonsstønad: Int?,
    val introduksjonsstønadEps: Int?,

    val kapitalinntekt: Int?,
    val kapitalinntektEps: Int?,

    val kontantstøtte: Int?,
    val kontantstøtteEps: Int?,

    val kvalifiseringsstønad: Int?,
    val kvalifiseringsstønadEps: Int?,

    val navYtelserTilLivsopphold: Int?,
    val navYtelserTilLivsoppholdEps: Int?,

    val offentligPensjon: Int?,
    val offentligPensjonEps: Int?,

    val privatPensjon: Int?,
    val privatPensjonEps: Int?,

    val sosialstønad: Int?,
    val sosialstønadEps: Int?,

    val statensLånekasse: Int?,
    val statensLånekasseEps: Int?,

    val supplerendeStønad: Int?,
    val supplerendeStønadEps: Int?,

    val sykepenger: Int?,
    val sykepengerEps: Int?,

    val tiltakspenger: Int?,
    val tiltakspengerEps: Int?,

    val ventestønad: Int?,
    val ventestønadEps: Int?,

    val uføretrygd: Int?,
    val uføretrygdEps: Int?,

    val forventetInntekt: Int?,
    val forventetInntektEps: Int?,

    val avkortingUtenlandsopphold: Int?,
    val avkortingUtenlandsoppholdEps: Int?,

    val underMinstenivå: Int?,
    val underMinstenivåEps: Int?,

    val annet: Int?,
    val annetEps: Int?,
)
