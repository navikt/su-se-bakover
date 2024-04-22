package no.nav.su.se.bakover.web.routes.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.regulering.ÅrsakTilManuellReguleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import vilkår.formue.domain.FormuegrenserFactory
import java.util.UUID

internal data class ReguleringJson(
    val id: UUID,
    val fnr: String,
    val opprettet: Tidspunkt,
    val beregning: BeregningJson?,
    val simulering: SimuleringJson?,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val reguleringstype: String,
    val årsakForManuell: List<ÅrsakTilManuellReguleringJson>,
    val reguleringsstatus: Status,
    val periode: PeriodeJson,
    val erFerdigstilt: Boolean,
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson,
    val saksbehandler: String,
    val avsluttet: Avsluttet?,
    val sakstype: String,
) {
    data class Avsluttet(val tidspunkt: Tidspunkt)
    enum class Status {
        OPPRETTET,
        IVERKSATT,
        AVSLUTTET,
        ;

        override fun toString(): String {
            return this.name
        }
    }
}

interface ÅrsakTilManuellReguleringJson {

    data object FradragMåHåndteresManuelt : ÅrsakTilManuellReguleringJson
    data object UtbetalingFeilet : ÅrsakTilManuellReguleringJson

    data class BrukerManglerSupplement(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class SupplementInneholderIkkeFradraget(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class FinnesFlerePerioderAvFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class FradragErUtenlandsinntekt(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class SupplementHarFlereVedtaksperioderForFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMedJson>,
    ) : ÅrsakTilManuellReguleringJson

    data class MismatchMellomBeløpFraSupplementOgFradrag(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterntBeløpFørRegulering: String,
        val vårtBeløpFørRegulering: String,
    ) : ÅrsakTilManuellReguleringJson

    data class BeløpErStørreEnForventet(
        val fradragstype: String,
        val fradragTilhører: String,
        val begrunnelse: String,
        val eksterntBeløpEtterRegulering: String,
        val forventetBeløpEtterRegulering: String,
    ) : ÅrsakTilManuellReguleringJson

    data class YtelseErMidlertidigStanset(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    data class ForventetInntektErStørreEnn0(
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    data class AutomatiskSendingTilUtbetalingFeilet(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class VedtakstidslinjeErIkkeSammenhengende(
        val begrunnelse: String,
    ) : ÅrsakTilManuellReguleringJson

    data class DelvisOpphør(
        val opphørsperioder: List<PeriodeJson>,
        val begrunnelse: String?,
    ) : ÅrsakTilManuellReguleringJson

    companion object {
        fun Set<ÅrsakTilManuellRegulering>.toJson(): List<ÅrsakTilManuellReguleringJson> = this.map { it.toJson() }

        fun ÅrsakTilManuellRegulering.toJson(): ÅrsakTilManuellReguleringJson = when (this) {
            is ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet -> AutomatiskSendingTilUtbetalingFeilet(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.DelvisOpphør -> DelvisOpphør(
                opphørsperioder = this.opphørsperioder.map { it.toJson() },
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0 -> ForventetInntektErStørreEnn0(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BeløpErStørreEnForventet -> BeløpErStørreEnForventet(
                begrunnelse = this.begrunnelse,
                fradragstype = this.fradragstype.kategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
                eksterntBeløpEtterRegulering = this.eksterntBeløpEtterRegulering.toString(),
                forventetBeløpEtterRegulering = this.forventetBeløpEtterRegulering.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement -> BrukerManglerSupplement(
                begrunnelse = this.begrunnelse,
                fradragstype = this.fradragstype.kategori.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag -> FinnesFlerePerioderAvFradrag(
                begrunnelse = this.begrunnelse,
                fradragstype = this.fradragstype.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt -> FradragErUtenlandsinntekt(
                begrunnelse = this.begrunnelse,
                fradragstype = this.fradragstype.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MismatchMellomBeløpFraSupplementOgFradrag -> MismatchMellomBeløpFraSupplementOgFradrag(
                begrunnelse = this.begrunnelse,
                fradragstype = this.fradragstype.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
                eksterntBeløpFørRegulering = this.eksterntBeløpFørRegulering.toString(),
                vårtBeløpFørRegulering = this.vårtBeløpFørRegulering.toString(),

            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag -> SupplementHarFlereVedtaksperioderForFradrag(
                begrunnelse = this.begrunnelse,
                fradragstype = this.fradragstype.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
                eksterneReguleringsvedtakperioder = this.eksterneReguleringsvedtakperioder.map { it.toJson() },
            )

            is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget -> SupplementInneholderIkkeFradraget(
                begrunnelse = this.begrunnelse,
                fradragstype = this.fradragstype.toString(),
                fradragTilhører = this.fradragTilhører.toString(),
            )

            is ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0 -> ForventetInntektErStørreEnn0(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt -> FradragMåHåndteresManuelt

            is ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet -> UtbetalingFeilet

            is ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende -> VedtakstidslinjeErIkkeSammenhengende(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset -> YtelseErMidlertidigStanset(
                begrunnelse = this.begrunnelse,
            )

            is ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset -> YtelseErMidlertidigStanset(
                begrunnelse = this.begrunnelse,
            )
        }
    }
}

internal fun Regulering.toJson(formuegrenserFactory: FormuegrenserFactory) = ReguleringJson(
    id = id.value,
    fnr = fnr.toString(),
    opprettet = opprettet,
    sakId = sakId,
    saksnummer = saksnummer,
    beregning = beregning?.toJson(),
    simulering = simulering?.toJson(),
    reguleringstype = when (reguleringstype) {
        is Reguleringstype.AUTOMATISK -> "AUTOMATISK"
        is Reguleringstype.MANUELL -> "MANUELL"
    },
    årsakForManuell = when (val type = reguleringstype) {
        Reguleringstype.AUTOMATISK -> emptyList()
        is Reguleringstype.MANUELL -> type.problemer.toJson()
    },
    reguleringsstatus = when (this) {
        is AvsluttetRegulering -> ReguleringJson.Status.AVSLUTTET
        is IverksattRegulering -> ReguleringJson.Status.IVERKSATT
        is OpprettetRegulering -> ReguleringJson.Status.OPPRETTET
    },
    erFerdigstilt = this.erFerdigstilt,
    periode = periode.toJson(),
    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerJson.create(
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        formuegrenserFactory = formuegrenserFactory,
    ),
    saksbehandler = saksbehandler.navIdent,
    avsluttet = when (this) {
        is AvsluttetRegulering -> ReguleringJson.Avsluttet(this.avsluttetTidspunkt)
        is IverksattRegulering, is OpprettetRegulering -> null
    },
    sakstype = sakstype.toJson(),
)
