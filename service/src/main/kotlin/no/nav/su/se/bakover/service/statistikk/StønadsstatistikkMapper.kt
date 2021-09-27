package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.Clock
import java.time.LocalDate

internal class StønadsstatistikkMapper(private val clock: Clock) {

    fun map(vedtak: Vedtak.EndringIYtelse, aktørId: AktørId, ytelseVirkningstidspunkt: LocalDate): Statistikk.Stønad {
        val nå = Tidspunkt.now(clock)

        return Statistikk.Stønad(
            funksjonellTid = nå,
            tekniskTid = nå,
            stonadstype = Statistikk.Stønad.Stønadstype.SU_UFØR,
            sakId = vedtak.behandling.sakId,
            aktorId = aktørId.toString().toLong(),
            sakstype = vedtakstype(vedtak),
            vedtaksdato = vedtak.opprettet.toLocalDate(zoneIdOslo),
            vedtakstype = vedtakstype(vedtak),
            vedtaksresultat = when (vedtak) {
                is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> TODO("Ikke implementert for ${vedtak::class}")
                is Vedtak.EndringIYtelse.InnvilgetRevurdering -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
                is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
                is Vedtak.EndringIYtelse.OpphørtRevurdering -> Statistikk.Stønad.Vedtaksresultat.OPPHØRT
                is Vedtak.EndringIYtelse.StansAvYtelse -> TODO("Ikke implementert for ${vedtak::class}")
            },
            behandlendeEnhetKode = "4815",
            ytelseVirkningstidspunkt = ytelseVirkningstidspunkt,
            gjeldendeStonadVirkningstidspunkt = vedtak.behandling.periode.fraOgMed,
            gjeldendeStonadStopptidspunkt = vedtak.behandling.periode.tilOgMed,
            gjeldendeStonadUtbetalingsstart = vedtak.behandling.periode.fraOgMed,
            gjeldendeStonadUtbetalingsstopp = vedtak.behandling.periode.tilOgMed,
            månedsbeløp = when (vedtak) {
                is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> TODO("Ikke implementert for ${vedtak::class}")
                is Vedtak.EndringIYtelse.InnvilgetRevurdering -> mapBeregning(vedtak, vedtak.beregning)
                is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> mapBeregning(vedtak, vedtak.beregning)
                is Vedtak.EndringIYtelse.OpphørtRevurdering -> mapBeregning(vedtak, vedtak.beregning)
                is Vedtak.EndringIYtelse.StansAvYtelse -> TODO("Ikke implementert for ${vedtak::class}")
            },
            versjon = nå.toEpochMilli(),
            opphorsgrunn = when (vedtak) {
                is Vedtak.EndringIYtelse.OpphørtRevurdering -> vedtak.behandling.utledOpphørsgrunner().joinToString()
                else -> null
            },
            opphorsdato = when (vedtak) {
                is Vedtak.EndringIYtelse.OpphørtRevurdering -> vedtak.behandling.utledOpphørsdato()
                else -> null
            },
        )
    }
}

private fun mapBeregning(vedtak: Vedtak.EndringIYtelse, beregning: Beregning): List<Statistikk.Stønad.Månedsbeløp> {
    return beregning.getMånedsberegninger().map {
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
    }
}

private fun vedtakstype(vedtak: Vedtak.EndringIYtelse) = when (vedtak) {
    is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> TODO("Ikke implementert for ${vedtak::class}")
    is Vedtak.EndringIYtelse.InnvilgetRevurdering -> Statistikk.Stønad.Vedtakstype.REVURDERING
    is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> Statistikk.Stønad.Vedtakstype.SØKNAD
    is Vedtak.EndringIYtelse.OpphørtRevurdering -> Statistikk.Stønad.Vedtakstype.REVURDERING
    is Vedtak.EndringIYtelse.StansAvYtelse -> TODO("Ikke implementert for ${vedtak::class}")
}

private fun stønadsklassifisering(
    behandling: Behandling,
    månedsberegning: Månedsberegning,
): Statistikk.Stønad.Stønadsklassifisering {
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
