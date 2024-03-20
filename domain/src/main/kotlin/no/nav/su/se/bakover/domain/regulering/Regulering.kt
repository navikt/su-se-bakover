package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.nonEmptyListOf
import arrow.core.right
import behandling.domain.Stønadsbehandling
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.absoluttDiff
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
            gVerdiØkning: BigDecimal,
        ): Either<LagerIkkeReguleringDaDenneUansettMåRevurderes, OpprettetRegulering> {
//            val reguleringstype =
//                gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent().fold(
//                    { konsistensproblemer ->
//                        val message =
//                            "Kunne ikke opprette regulering for saksnummer $saksnummer." +
//                                " Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]"
//                        if (konsistensproblemer.erGyldigTilstand()) {
//                            log.info(message)
//                        } else {
//                            log.error(message)
//                        }
//                        return LagerIkkeReguleringDaDenneUansettMåRevurderes.left()
//                    },
//                    {
//                        gjeldendeVedtaksdata.utledReguleringstype(eksternSupplementRegulering)
//                    },
//                )
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
                        gVerdiØkning = gVerdiØkning,
                    )
                    oppdaterteFradragsgrunnlag
                }.let {
                    val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
                        Reguleringstype.MANUELL(
                            problemer = it.filterIsInstance<Reguleringstype.MANUELL>().flatMap { it.problemer }.toSet(),
                        )
                    } else {
                        Reguleringstype.AUTOMATISK
                    }
                    reguleringstype to it.flatMap { it.second }
                }

            println("$reguleringstypeVedSupplement, $oppdatereFradragsgrunnlaggKunForSøker")

            // TODO må oppdatere grunnlagene med verdiene i supplementet
            return OpprettetRegulering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
                fnr = fnr,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                beregning = null,
                simulering = null,
                reguleringstype = reguleringstypeVedSupplement,
                sakstype = sakstype,
                eksternSupplementRegulering = eksternSupplementRegulering,
            ).right()
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
    gVerdiØkning: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })

    return originaleFradragsgrunnlag.groupBy { it.fradrag.tilhører }.map { (fradragtilhører, fradragsgrunnlag) ->
        utledReguleringstypeOgFradrag(
            eksternSupplementRegulering,
            fradragstype,
            fradragsgrunnlag.toNonEmptyList(),
            fradragtilhører,
            periodeTilEps,
            gVerdiØkning,
        )
    }.let {
        val reguleringstype = if (it.any { it.first is Reguleringstype.MANUELL }) {
            Reguleringstype.MANUELL(
                problemer = it.filterIsInstance<Reguleringstype.MANUELL>().flatMap { it.problemer }.toSet(),
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
    gVerdiØkning: BigDecimal,
): Pair<Reguleringstype, List<Fradragsgrunnlag>> {
    require(originaleFradragsgrunnlag.all { it.fradragstype == fradragstype })
    require(originaleFradragsgrunnlag.all { it.fradrag.tilhører == fradragTilhører })

    if (
        (eksternSupplementRegulering.bruker == null && fradragTilhører == FradragTilhører.BRUKER) ||
        (eksternSupplementRegulering.eps.isEmpty() && fradragTilhører == FradragTilhører.EPS)
    ) {
        // Dette er den vanligste casen for manuell regulering, vi trenger ikke logge disse tilfellene.
        val regtype =
            if (fradragstype.måJusteresManueltVedGEndring) Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt)) else Reguleringstype.AUTOMATISK
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
        gVerdiØkning,
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
    gVerdiØkning: BigDecimal,
): Pair<Reguleringstype, Fradragsgrunnlag> {
    require(originaleFradragsgrunnlag.fradragstype == fradragstype)
    require(originaleFradragsgrunnlag.fradrag.tilhører == fradragTilhører)
    require(supplement.bruker != null || supplement.eps.isNotEmpty())

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
    val supplementBeløp = supplementForType.fradragsperioder.single().beløp.toBigDecimal()

    val diffØkning =
        supplementBeløp.divide(BigDecimal(originaleFradragsgrunnlag.månedsbeløp).setScale(2), RoundingMode.HALF_UP)
    if (diffØkning.absoluttDiff(gVerdiØkning) > BigDecimal("1.01")) {
        return Reguleringstype.MANUELL(setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt)) to originaleFradragsgrunnlag
    }

    return Reguleringstype.AUTOMATISK to originaleFradragsgrunnlag.oppdaterBeløpFraSupplement(supplementBeløp)

//    supplement.bruker?.let { supplementSøker ->
//        supplementSøker.perType.flatMap { perType ->
//            if (perType.type == fradragstype) {
//                originaleFradragsgrunnlag.whenSingleOrMultiple(
//                    isSingle = {
//                        val grunnlaget = originaleFradragsgrunnlag.single()
//                        perType.fradragsperioder.whenSingleOrMultiple(
//                            isSingle = {
//                                /**
//                                 * Identifiser at typen er bare 1, og fradraget er bare 1, da kan vi direkte oppdatere
//                                 * fradraget med det nye beløpet.
//                                 *
//                                 * TODO sjekk diffen på tilsvarende grunnalgsendringen
//                                 *  - dersom diffen er høyere, går den til manuell.
//                                 */
//                                Reguleringstype.AUTOMATISK to listOf(grunnlaget.oppdaterBeløpFraSupplement(it.beløp))
//                            },
//                            isMultiple = {
//                                /**
//                                 * identifisert at vi har kun 1 fradragsgrunnlag, men flere fra supplementet.
//                                 * Er dette et gyldig case???
//                                 */
//                                TODO()
//                            },
//                        )
//                    },
//                    isMultiple = {
//                        /**
//                         * identifisert at vi har flere fradrag av samme type der perioden, eller innholdet ikke matcher.
//                         * Vi slår ikke sammen fradragene dersom perioden er lik, men beløpene er ulikt. Vi burde kanskje ha lagt dem til?
//                         *
//                         */
//                        TODO()
//                    },
//                )
//            } else {
//                originaleFradragsgrunnlag
//            }.toNonEmptyList()
//        }
//    }
}
