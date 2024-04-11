package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import behandling.domain.Stønadsbehandling
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.bosituasjon.domain.grunnlag.periodeTilEpsFnr
import vilkår.common.domain.Vurdering
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
import vilkår.vurderinger.domain.erGyldigTilstand
import økonomi.domain.simulering.Simulering
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.util.UUID

fun Regulering.inneholderAvslag(): Boolean = this.vilkårsvurderinger.resultat() is Vurdering.Avslag

/**
 * Det knyttes et slikt objekt til hver regulering, både manuelle og automatiske, eller null dersom vi ikke har slike data.
 * Den vil være basert på eksterne data (både fil og tjenester). Merk at det er viktig og lagre originaldata, f.eks. i hendelser.
 *
 * @property bruker reguleringsdata/fradrag fra eksterne kilder for bruker. Kan være null dersom bruker ikke har fradrag fra eksterne kilder.
 * @property eps reguleringsdata/fradrag fra eksterne kilder for ingen, en eller flere EPS, eller vi har hentet regulerte fradrag på EPS.
 */
data class EksternSupplementRegulering(
    val bruker: ReguleringssupplementFor?,
    val eps: List<ReguleringssupplementFor>,
) {
    fun hentForEps(fnr: Fnr): ReguleringssupplementFor? = eps.find { it.fnr == fnr }
}

private val log: Logger = LoggerFactory.getLogger("Regulering")

sealed interface Regulering : Stønadsbehandling {
    override val id: ReguleringId
    override val beregning: Beregning?
    override val simulering: Simulering?
    override val eksterneGrunnlag: EksterneGrunnlag get() = StøtterIkkeHentingAvEksternGrunnlag
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering
    override val vilkårsvurderinger: VilkårsvurderingerRevurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringstype: Reguleringstype

    /**
     * Supplementet inneholder informasjon som skal brukes for å oppdatere grunnlagene
     * Supplementet hentes fra eksterne kilder
     */
    val eksternSupplementRegulering: EksternSupplementRegulering

    fun erÅpen(): Boolean

    /** true dersom dette er en iverksatt regulering, false ellers. */
    val erFerdigstilt: Boolean

    companion object {

        /**
         * @param clock Brukes kun dersom [opprettet] ikke sendes inn.
         */
        fun opprettRegulering(
            id: ReguleringId = ReguleringId.generer(),
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            clock: Clock,
            opprettet: Tidspunkt = Tidspunkt.now(clock),
            sakstype: Sakstype,
            eksternSupplementRegulering: EksternSupplementRegulering,
            omregningsfaktor: BigDecimal,
        ): Either<LagerIkkeReguleringDaDenneUansettMåRevurderes, OpprettetRegulering> {
            val reguleringstypeVedGenerelleProblemer =
                getReguleringstypeVedGenerelleProblemer(
                    gjeldendeVedtaksdata,
                    saksnummer,
                    eksternSupplementRegulering,
                ).getOrElse {
                    return it.left()
                }
            val fradrag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag
            val bosituasjon = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon

            /**
             * TODO
             *  Perioden vi får inn per type vil potensielt være lenger enn våres periode, eller kortere, fordi at Pesys, legger på
             *  tilOgMed til siste dagen i året. Våres periode følger naturligvis stønadsperioden, som vil kunne gjelde over pesys sin tilOgMed
             *  vi kan få samme fradrag flere ganger. hull i perioden er en mulighet. kan prøve å slå sammen fradragene til 1.
             *  hvis ikke det lar seg gjøre, kan vi sette reguleringen til manuell.
             *  Eventuelt gjøre periodene om til måneder, oppdatere beløpene. Merk at samme problem stilling med perioder i pesys vs våres fortsatt gjelder.
             *
             */
                val (reguleringstypeVedSupplement, oppdatereFradragsgrunnlaggKunForSøker) = fradrag
                .groupBy { it.fradragstype }
                .map { (fradragstype, fradragsgrunnlag) ->
                    val oppdaterteFradragsgrunnlag = utledReguleringstypeOgFradrag(
                        eksternSupplementRegulering = eksternSupplementRegulering,
                        fradragstype = fradragstype,
                        originaleFradragsgrunnlag = fradragsgrunnlag.toNonEmptyList(),
                        periodeTilEps = bosituasjon.periodeTilEpsFnr(),
                        omregningsfaktor = omregningsfaktor,
                    )
                    oppdaterteFradragsgrunnlag
                }.let {
                    val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
                        Reguleringstype.MANUELL(
                            problemer = it.map { it.first }.filterIsInstance<Reguleringstype.MANUELL>()
                                .flatMap { it.problemer }.toSet(),
                        )
                    } else {
                        Reguleringstype.AUTOMATISK
                    }
                    reguleringstype to it.flatMap { it.second }
                }

            // utledning av reguleringstype bør gjøre mer helhetlig, og muligens kun 1 gang. Dette er en midlertidig løsning.
            val reguleringstype = Reguleringstype.utledReguleringsTypeFrom(
                reguleringstype1 = reguleringstypeVedGenerelleProblemer,
                reguleringstype2 = reguleringstypeVedSupplement,
            )
            return OpprettetRegulering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
                fnr = fnr,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.oppdaterGrunnlagsdata(
                    grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata.copy(
                        fradragsgrunnlag = oppdatereFradragsgrunnlaggKunForSøker,
                    ),
                ),
                beregning = null,
                simulering = null,
                reguleringstype = reguleringstype,
                sakstype = sakstype,
                eksternSupplementRegulering = eksternSupplementRegulering,
            ).right()
        }

        private fun getReguleringstypeVedGenerelleProblemer(
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            saksnummer: Saksnummer,
            eksternSupplementRegulering: EksternSupplementRegulering,
        ): Either<LagerIkkeReguleringDaDenneUansettMåRevurderes, Reguleringstype> {
            return gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent().fold(
                { konsistensproblemer ->
                    val message =
                        "Kunne ikke opprette regulering for saksnummer $saksnummer." +
                            " Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]"
                    if (konsistensproblemer.erGyldigTilstand()) {
                        log.info(message)
                    } else {
                        log.error(message)
                    }
                    return LagerIkkeReguleringDaDenneUansettMåRevurderes.left()
                },
                {
                    gjeldendeVedtaksdata.utledReguleringstype(eksternSupplementRegulering).right()
                },
            )
        }
    }

    data object LagerIkkeReguleringDaDenneUansettMåRevurderes
}

/**
 * Siden parameterene er gruppert på fradragstype, kan de tilhøre både ektefelle og bruker.
 *
 * @throws IllegalStateException Dersom typene til fradragsgrunnlagene ikke er den samme som [fradragstype]
 */
fun utledReguleringstypeOgFradrag(
    eksternSupplementRegulering: EksternSupplementRegulering,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Nel<Fradragsgrunnlag>,
    periodeTilEps: Map<Periode, Fnr>,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })

    return originaleFradragsgrunnlag.groupBy { it.fradrag.tilhører }.map { (fradragtilhører, fradragsgrunnlag) ->
        utledReguleringstypeOgFradrag(
            eksternSupplementRegulering,
            fradragstype,
            fradragsgrunnlag.toNonEmptyList(),
            fradragtilhører,
            periodeTilEps,
            omregningsfaktor,
        )
    }.let {
        val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
            Reguleringstype.MANUELL(
                problemer = it.map { it.first }.filterIsInstance<Reguleringstype.MANUELL>().flatMap { it.problemer }
                    .toSet(),
            )
        } else {
            Reguleringstype.AUTOMATISK
        }
        reguleringstype to it.flatMap { it.second }
    }
}

/**
 * Siden parameterene er gruppert på fradragstype, kan de tilhøre både ektefelle og bruker.
 *
 * @throws IllegalStateException Dersom typene til fradragsgrunnlagene ikke er den samme som [fradragstype]
 * @throws IllegalStateException Dersom fradragsgrunnlagene ikke matcher med [fradragTilhører]
 */
fun utledReguleringstypeOgFradrag(
    eksternSupplementRegulering: EksternSupplementRegulering,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Nel<Fradragsgrunnlag>,
    fradragTilhører: FradragTilhører,
    periodeTilEps: Map<Periode, Fnr>,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })
    require(originaleFradragsgrunnlag.all { it.fradrag.tilhører == fradragTilhører })

    if (
        (eksternSupplementRegulering.bruker == null && fradragTilhører == FradragTilhører.BRUKER) ||
        (eksternSupplementRegulering.eps.isEmpty() && fradragTilhører == FradragTilhører.EPS)
    ) {
        // Dette er den vanligste casen for manuell regulering, vi trenger ikke logge disse tilfellene.
        val regtype =
            if (fradragstype.måJusteresManueltVedGEndring) {
                Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt))
            } else {
                Reguleringstype.AUTOMATISK
            }
        return regtype to originaleFradragsgrunnlag
    }

    if (originaleFradragsgrunnlag.size > 1) {
        log.error("Regulering, utled type og fradrag: Vi oppdaget et fradrag som må reguleres som også finnes i Pesys-datasettet. Siden fradragsgrunnlaget vårt var delt opp i flere perioder, setter vi denne til manuelt.")
        return Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt)) to originaleFradragsgrunnlag
    }

    return utledReguleringstypeOgFradragForEttFradragsgrunnlag(
        eksternSupplementRegulering,
        fradragstype,
        originaleFradragsgrunnlag.first(),
        fradragTilhører,
        periodeTilEps,
        omregningsfaktor,
    ).let {
        it.first to nonEmptyListOf(it.second)
    }
}

/**
 * Siden parameterene er gruppert på fradragstype, kan de tilhøre både ektefelle og bruker.
 *
 * @throws IllegalStateException Dersom typene til fradragsgrunnlagene ikke er den samme som [fradragstype]
 * @throws IllegalStateException Dersom fradragsgrunnlagene ikke matcher med [fradragTilhører]
 */
fun utledReguleringstypeOgFradragForEttFradragsgrunnlag(
    supplement: EksternSupplementRegulering,
    fradragstype: Fradragstype,
    originaleFradragsgrunnlag: Fradragsgrunnlag,
    fradragTilhører: FradragTilhører,
    periodeTilEps: Map<Periode, Fnr>,
    omregningsfaktor: BigDecimal,
): Pair<Reguleringstype, Fradragsgrunnlag> {
    require(originaleFradragsgrunnlag.fradragstype == fradragstype)
    require(originaleFradragsgrunnlag.fradrag.tilhører == fradragTilhører)
    require(supplement.bruker != null || supplement.eps.isNotEmpty())

    if (originaleFradragsgrunnlag.utenlandskInntekt != null) {
        return Reguleringstype.MANUELL(
            setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt),
        ) to originaleFradragsgrunnlag
    }
    if (!originaleFradragsgrunnlag.skalJusteresVedGEndring()) {
        return Reguleringstype.AUTOMATISK to originaleFradragsgrunnlag
    }

    val supplementFor: ReguleringssupplementFor = when (fradragTilhører) {
        FradragTilhører.BRUKER -> supplement.bruker!!
        FradragTilhører.EPS -> supplement.hentForEps(periodeTilEps[originaleFradragsgrunnlag.periode]!!)!!
    }

    val supplementForType: ReguleringssupplementFor.PerType =
        supplementFor.getForType(fradragstype) ?: return Reguleringstype.MANUELL(
            setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt),
        ) to originaleFradragsgrunnlag

    if (supplementForType.perioder.size > 1) {
        // TODO jah: Vurder logging her
        return Reguleringstype.MANUELL(
            setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt),
        ) to originaleFradragsgrunnlag
    }
    val nåværendeFradragsbeløp = BigDecimal(originaleFradragsgrunnlag.fradrag.månedsbeløp).setScale(2)
    val supplementBeløp = supplementForType.fradragsperioder.single().beløp.toBigDecimal()

    val forventetBeløpBasertPåGverdi = (nåværendeFradragsbeløp * omregningsfaktor).setScale(2, RoundingMode.HALF_UP)

    val differanseSupplementOgForventet = supplementBeløp.subtract(forventetBeløpBasertPåGverdi).abs()

    if (differanseSupplementOgForventet > BigDecimal.TEN) {
        return Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt)) to originaleFradragsgrunnlag
    }

    val oppdatertBeløpFraSupplement = originaleFradragsgrunnlag.oppdaterBeløpFraSupplement(supplementBeløp)
    return Reguleringstype.AUTOMATISK to oppdatertBeløpFraSupplement
}
