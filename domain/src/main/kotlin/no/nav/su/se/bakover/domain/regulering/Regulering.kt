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
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
 * @param supplementId Id'en til [Reguleringssupplement] denne ble hentet ut ifra. Den kan være null ved historiske reguleringer.
 * @param bruker reguleringsdata/fradrag fra eksterne kilder for bruker. Kan være null dersom bruker ikke har fradrag fra eksterne kilder.
 * @param eps reguleringsdata/fradrag fra eksterne kilder for ingen, en eller flere EPS, eller vi har hentet regulerte fradrag på EPS.
 */
data class EksternSupplementRegulering(
    val supplementId: UUID?,
    val bruker: ReguleringssupplementFor?,
    // TODO jah - Bør kanskje ha en sjekk på at fnr er unike på tvers av eps og bruker?
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

    // TODO jah - Bør nok lagre at det er denne vi har brukt og
    fun oppdaterMedSupplement(
        eksternSupplementRegulering: EksternSupplementRegulering,
        omregningsfaktor: BigDecimal,
    ): OpprettetRegulering

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
                ).getOrElse {
                    return it.left()
                }
            val fradrag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag
            val bosituasjon = gjeldendeVedtaksdata.grunnlagsdata.bosituasjonSomFullstendig()

            val (reguleringstypeVedSupplement, fradragEtterSupplementSjekk) = utledReguleringstypeOgFradragVedHjelpAvSupplement(
                fradrag = fradrag,
                bosituasjon = bosituasjon,
                eksternSupplementRegulering = eksternSupplementRegulering,
                omregningsfaktor = omregningsfaktor,
            )

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
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.oppdaterFradragsgrunnlag(
                    fradragEtterSupplementSjekk,
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
                    gjeldendeVedtaksdata.utledReguleringstype().right()
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
                manuellReg(
                    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
                        fradragskategori = fradragstype.kategori,
                        fradragTilhører = fradragTilhører,
                        begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} påvirkes av samme sats/G-verdi endring som SU. Vi mangler supplement for dette fradraget og derfor går det til manuell regulering.",
                    ),
                )
            } else {
                Reguleringstype.AUTOMATISK
            }
        return regtype to originaleFradragsgrunnlag
    }

    if (originaleFradragsgrunnlag.size > 1) {
        log.error("Regulering, utled type og fradrag: Vi oppdaget et fradrag som må reguleres som også finnes i Pesys-datasettet. Siden fradragsgrunnlaget vårt var delt opp i flere perioder, setter vi denne til manuelt.")
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget til $fradragTilhører: ${fradragstype.kategori} er delt opp i flere perioder. Disse går foreløpig til manuell regulering.",
            ),
        ) to originaleFradragsgrunnlag
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
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Fradraget er utenlandsinntekt og går til manuell regulering",
            ),
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
        supplementFor.getForType(fradragstype) ?: return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                begrunnelse = "Vi fant et supplement for $fradragTilhører, men ikke for ${fradragstype.kategori}.",
            ),
        ) to originaleFradragsgrunnlag
    val antallEksterneReguleringsvedtak = supplementForType.reguleringsvedtak.size
    if (antallEksterneReguleringsvedtak > 1) {
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                eksterneReguleringsvedtakperioder = supplementForType.reguleringsvedtak.map { it.periode },
                begrunnelse = "Vi fant et supplement for $fradragTilhører og denne ${fradragstype.kategori}, men siden vi fant mer enn en vedtaksperiode ($antallEksterneReguleringsvedtak), går den til manuell.",
            ),
        ) to originaleFradragsgrunnlag
    }
    val vårtBeløpFørRegulering = BigDecimal(originaleFradragsgrunnlag.fradrag.månedsbeløp).setScale(2)
    val eksterntBeløpFørRegulering = supplementForType.endringsvedtak?.beløp ?: return manuellReg(
        ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FantIkkeVedtakForApril(
            fradragskategori = fradragstype.kategori,
            fradragTilhører = fradragTilhører,
            begrunnelse = "Vi fant et supplement for $fradragTilhører og denne ${fradragstype.kategori} - men vi fant ikke et eksternt endringsvedtak for april for å regne ut reguleringen.",
        ),
    ) to originaleFradragsgrunnlag
    val diffFørRegulering = eksterntBeløpFørRegulering - vårtBeløpFørRegulering.intValueExact()
    // vi skal ikke akseptere differanse fra eksterne kilde og vårt beløp
    if (diffFørRegulering > 0) {
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                vårtBeløpFørRegulering = vårtBeløpFørRegulering,
                eksterntBeløpFørRegulering = eksterntBeløpFørRegulering.toBigDecimal(),
                begrunnelse = "Vi forventet at beløpet skulle være $vårtBeløpFørRegulering før regulering, men det var $eksterntBeløpFørRegulering. Vi aksepterer ikke en differanse, men differansen var $diffFørRegulering",
            ),
        ) to originaleFradragsgrunnlag
    }
    val eksterntBeløpEtterRegulering = supplementForType.reguleringsvedtak.single().beløp.toBigDecimal()

    val forventetBeløpBasertPåGverdi = (vårtBeløpFørRegulering * omregningsfaktor).setScale(2, RoundingMode.HALF_UP)

    val differanseSupplementOgForventet = eksterntBeløpEtterRegulering.subtract(forventetBeløpBasertPåGverdi).abs()

    val akseptertDifferanseEtterRegulering = BigDecimal.TEN
    if (differanseSupplementOgForventet > akseptertDifferanseEtterRegulering) {
        return manuellReg(
            ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
                fradragskategori = fradragstype.kategori,
                fradragTilhører = fradragTilhører,
                forventetBeløpEtterRegulering = forventetBeløpBasertPåGverdi,
                eksterntBeløpEtterRegulering = eksterntBeløpEtterRegulering,
                begrunnelse = "Vi forventet at beløpet skulle være $forventetBeløpBasertPåGverdi etter regulering, men det var $eksterntBeløpEtterRegulering. Vi aksepterer en differanse på $akseptertDifferanseEtterRegulering, men den var $differanseSupplementOgForventet",
            ),
        ) to originaleFradragsgrunnlag
    }

    val oppdatertBeløpFraSupplement = originaleFradragsgrunnlag.oppdaterBeløpFraSupplement(eksterntBeløpEtterRegulering)
    return Reguleringstype.AUTOMATISK to oppdatertBeløpFraSupplement
}

private fun manuellReg(årsak: ÅrsakTilManuellRegulering): Reguleringstype.MANUELL =
    Reguleringstype.MANUELL(setOf(årsak))
