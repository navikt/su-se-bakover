package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats.Companion.utledSats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToLong

internal class StønadsstatistikkMapper(private val clock: Clock) {
    val nå = Tidspunkt.now(clock)

    // TODO: Heller en map per type?
    fun map(behandling: Behandling): Statistikk.Stønad = Statistikk.Stønad(
        funksjonellTid = nå,
        tekniskTid = nå,
        stonadstype = Statistikk.Stønad.Stønadstype.SU_UFØR,
        sakId = behandling.sakId,
        aktorId = 9999999, // MÅ FINNE AKTØR-ID FRA FNR?
        sakstype = vedtakstype(behandling),
        vedtaksdato = LocalDate.now(clock),
        vedtakstype = vedtakstype(behandling),
        vedtaksresultat = vedtaksresultat(behandling),
        behandlendeEnhetKode = "4815",
        ytelseVirkningstidspunkt = LocalDate.now(), // MÅ FINNE FØRSTE VIRKNINGSTIDSPUNKT FOR YTELSEN, FINNE FØRST FOM PÅ VEDTAK/BEHANDLINGER PÅ SAKEN?
        gjeldendeStonadVirkningstidspunkt = behandling.periode.fraOgMed,
        gjeldendeStonadStopptidspunkt = behandling.periode.tilOgMed,
        gjeldendeStonadUtbetalingsstart = behandling.periode.fraOgMed,
        gjeldendeStonadUtbetalingsstopp = behandling.periode.tilOgMed,
        stonadsklassifisering = stønadsklassifisering(behandling),
        bruttosatsMnd = bosituasjon(behandling).utledSats().månedsbeløpSomHeltall(behandling.periode.fraOgMed).toLong(), // BURDE ALLE DISSE EGENTLIG VÆRE EN LISTE MED MÅNEDSBELØP?
        nettosatsMnd = beregning(behandling).getMånedsberegninger().first().getSumYtelse().toLong(), // MÅ SPØRRE STØNADSSTATISTIKK HVA VI SKAL GJØRE MED VARIERENDE BELØP PER MÅNED
        inntekter = listOf(Statistikk.Inntekt("Arbeidsinntekt", 5000)),
        fradragSum = beregning(behandling).getSumFradrag().roundToLong(),
        fullSats = bosituasjon(behandling).utledSats().årsbeløp(behandling.periode.fraOgMed).roundToLong(),
    )

    private fun vedtakstype(behandling: Behandling): Statistikk.Stønad.Vedtakstype {
        return when (behandling) {
            is Søknadsbehandling -> Statistikk.Stønad.Vedtakstype.SØKNAD
            is Revurdering -> Statistikk.Stønad.Vedtakstype.REVURDERING
            else -> {
                log.error("Fant ikke vedtakstype for behandling " + behandling.id)
                throw RuntimeException("Fant ikke vedtakstype for behandling")
            }
        }
    }

    private fun vedtaksresultat(behandling: Behandling): Statistikk.Stønad.Vedtaksresultat {
        return when (behandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
            is IverksattRevurdering.Innvilget -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
            is IverksattRevurdering.Opphørt -> Statistikk.Stønad.Vedtaksresultat.OPPHØRT

            else -> {
                log.error("Fant ikke vedtaksresultat for behandling " + behandling.id)
                throw RuntimeException("Fant ikke vedtaksresultat for behandling")
            }
        }
    }

    private fun bosituasjon(behandling: Behandling): Grunnlag.Bosituasjon.Fullstendig {
        // TODO: first WTF
        return (behandling.grunnlagsdata.bosituasjon.first() as Grunnlag.Bosituasjon.Fullstendig)
    }

    private fun stønadsklassifisering(behandling: Behandling): Statistikk.Stønad.Stønadsklassifisering {
        return when (bosituasjon(behandling)) {
            is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Statistikk.Stønad.Stønadsklassifisering.BOR_MED_ANDRE_VOKSNE
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Statistikk.Stønad.Stønadsklassifisering.BOR_MED_EKTEFELLE_UNDER_67_IKKE_UFØR_FLYKTNING
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> Statistikk.Stønad.Stønadsklassifisering.BOR_MED_EKTEFELLE_OVER_67
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> Statistikk.Stønad.Stønadsklassifisering.BOR_MED_EKTEFELLE_UNDER_67_UFØR_FLYKTNING
            is Grunnlag.Bosituasjon.Fullstendig.Enslig -> Statistikk.Stønad.Stønadsklassifisering.BOR_ALENE
            else -> {
                log.error("Fant ikke stønadsklassifisering for behandling " + behandling.id)
                throw RuntimeException("Fant ikke stønadsklassifisering for behandling")
            }
        }
    }

    private fun beregning(behandling: Behandling): Beregning {
        return when (behandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> behandling.beregning
            is IverksattRevurdering -> behandling.beregning
            else -> {
                log.error("Fant ikke beregning for behandling " + behandling.id)
                throw RuntimeException("Fant ikke beregning for behandling")
            }
        }
    }
}
