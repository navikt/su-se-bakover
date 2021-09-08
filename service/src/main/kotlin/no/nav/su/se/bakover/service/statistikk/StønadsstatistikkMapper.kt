package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import java.time.Clock
import java.time.LocalDate

internal class StønadsstatistikkMapper(clock: Clock) {
    val nå = Tidspunkt.now(clock)

    fun map(vedtak: Vedtak.EndringIYtelse, aktørId: AktørId, ytelseVirkningstidspunkt: LocalDate): Statistikk.Stønad {
        return Statistikk.Stønad(
            funksjonellTid = nå,
            tekniskTid = nå,
            stonadstype = Statistikk.Stønad.Stønadstype.SU_UFØR,
            sakId = vedtak.behandling.sakId,
            aktorId = aktørId.toString().toLong(),
            sakstype = vedtakstype(vedtak.vedtakType),
            vedtaksdato = vedtak.opprettet.toLocalDate(zoneIdOslo),
            vedtakstype = vedtakstype(vedtak.vedtakType),
            vedtaksresultat = when (vedtak.vedtakType) {
                VedtakType.SØKNAD -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
                VedtakType.ENDRING -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
                VedtakType.OPPHØR -> Statistikk.Stønad.Vedtaksresultat.OPPHØRT
                else -> throw RuntimeException("Ugyldig vedtaksresultat")
            },
            behandlendeEnhetKode = "4815",
            ytelseVirkningstidspunkt = ytelseVirkningstidspunkt,
            gjeldendeStonadVirkningstidspunkt = vedtak.behandling.periode.fraOgMed,
            gjeldendeStonadStopptidspunkt = vedtak.behandling.periode.tilOgMed,
            gjeldendeStonadUtbetalingsstart = vedtak.behandling.periode.fraOgMed,
            gjeldendeStonadUtbetalingsstopp = vedtak.behandling.periode.tilOgMed,
            månedsbeløp = vedtak.beregning.getMånedsberegninger().map {
                Statistikk.Stønad.Månedsbeløp(
                    måned = it.periode.fraOgMed.toString(),
                    stonadsklassifisering = stønadsklassifisering(vedtak.behandling, it),
                    bruttosats = it.getSatsbeløp().toLong(),
                    nettosats = it.getSumYtelse().toLong(),
                    inntekter = it.getFradrag().map { fradrag ->
                        Statistikk.Inntekt(
                            inntektstype = fradrag.fradragstype.toString(),
                            beløp = fradrag.månedsbeløp.toLong(),
                        )
                    },
                    fradragSum = it.getSumFradrag().toLong(),
                )
            },
            versjon = nå.toEpochMilli(),
            opphorsgrunn = when (vedtak.vedtakType) {
                VedtakType.OPPHØR -> (vedtak.behandling as IverksattRevurdering.Opphørt).utledOpphørsgrunner()
                    .joinToString()
                else -> null
            },
            opphorsdato = when (vedtak.vedtakType) {
                VedtakType.OPPHØR -> (vedtak.behandling as IverksattRevurdering.Opphørt).utledOpphørsdato()
                else -> null
            },
        )
    }

    private fun vedtakstype(vedtakType: VedtakType) = when (vedtakType) {
        VedtakType.SØKNAD -> Statistikk.Stønad.Vedtakstype.SØKNAD
        VedtakType.ENDRING -> Statistikk.Stønad.Vedtakstype.REVURDERING
        VedtakType.OPPHØR -> Statistikk.Stønad.Vedtakstype.REVURDERING
        else -> throw RuntimeException("Ugyldig vedtakstype")
    }

    private fun stønadsklassifisering(behandling: Behandling, månedsberegning: Månedsberegning): Statistikk.Stønad.Stønadsklassifisering {
        val bosituasjon = behandling.grunnlagsdata.bosituasjon.single {
            it.periode inneholder månedsberegning.periode
        }

        return when (bosituasjon) {
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
}
