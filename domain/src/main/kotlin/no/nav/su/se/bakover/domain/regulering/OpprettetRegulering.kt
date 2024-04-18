package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import behandling.regulering.domain.beregning.KunneIkkeBeregneRegulering
import behandling.regulering.domain.simulering.KunneIkkeSimulereRegulering
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import beregning.domain.Beregning
import beregning.domain.BeregningStrategyFactory
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import satser.domain.SatsFactory
import vilkår.common.domain.Vurdering
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.VurderingsperiodeUføre
import vilkår.vurderinger.domain.Grunnlagsdata
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.simulering.Simuleringsresultat
import økonomi.domain.utbetaling.Utbetaling
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

data class OpprettetRegulering(
    override val id: ReguleringId,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
    override val periode: Periode = grunnlagsdataOgVilkårsvurderinger.periode()!!,
    override val beregning: Beregning?,
    override val simulering: Simulering?,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val reguleringstype: Reguleringstype,
    override val sakstype: Sakstype,
    override val eksternSupplementRegulering: EksternSupplementRegulering,
) : Regulering {
    override fun erÅpen() = true

    override fun oppdaterMedSupplement(
        eksternSupplementRegulering: EksternSupplementRegulering,
        omregningsfaktor: BigDecimal,
    ): OpprettetRegulering {
        /**
         * Burde vi kanskje ta inn disse som er gjeldende på saken istedenfor?
         */
        val fradrag = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag
        val bosituasjon = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjonSomFullstendig()

        val (reguleringstypeVedSupplement, fradragEtterSupplementSjekk) = utledReguleringstypeOgFradragVedHjelpAvSupplement(
            fradrag = fradrag,
            bosituasjon = bosituasjon,
            eksternSupplementRegulering = eksternSupplementRegulering,
            omregningsfaktor = omregningsfaktor,
        )
        return this.copy(
            eksternSupplementRegulering = eksternSupplementRegulering,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                grunnlagsdata = Grunnlagsdata.tryCreate(
                    bosituasjon = bosituasjon,
                    fradragsgrunnlag = fradragEtterSupplementSjekk,
                ).getOrElse { throw IllegalStateException("Kunne ikke legge til fradrag ved regulering: $it") },
                vilkårsvurderinger = vilkårsvurderinger,
            ),
            reguleringstype = reguleringstypeVedSupplement,
        )
    }

    /**
     * Skal ikke sende brev ved regulering.
     */
    override fun skalSendeVedtaksbrev(): Boolean {
        return false
    }

    override val erFerdigstilt = false

    init {
        if (reguleringstype == Reguleringstype.AUTOMATISK) {
            require(vilkårsvurderinger.resultat() is Vurdering.Innvilget)
        }
        require(grunnlagsdataOgVilkårsvurderinger.erVurdert())
        require(periode == grunnlagsdataOgVilkårsvurderinger.periode())
        beregning?.let { require(periode == beregning.periode) }
    }

    fun leggTilFradrag(fradragsgrunnlag: List<Fradragsgrunnlag>): OpprettetRegulering =
        this.copy(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                grunnlagsdata = Grunnlagsdata.tryCreate(
                    bosituasjon = grunnlagsdata.bosituasjonSomFullstendig(),
                    fradragsgrunnlag = fradragsgrunnlag,
                ).getOrElse { throw IllegalStateException("Kunne ikke legge til fradrag ved regulering: $it") },
                vilkårsvurderinger = vilkårsvurderinger,
            ),
        )

    fun leggTilUføre(uføregrunnlag: List<Uføregrunnlag>, clock: Clock): OpprettetRegulering {
        sikkerLogg.debug(
            "Skal legge til {} for regulering {}. Vilkår & grunnlag som er på behandling NÅ: {}, {}",
            uføregrunnlag,
            this.id,
            grunnlagsdata,
            vilkårsvurderinger,
        )

        return this.copy(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(
                    UføreVilkår.Vurdert.tryCreate(
                        uføregrunnlag.map {
                            VurderingsperiodeUføre.tryCreate(
                                opprettet = Tidspunkt.now(clock),
                                vurdering = Vurdering.Innvilget,
                                grunnlag = it,
                                vurderingsperiode = it.periode,
                            ).getOrElse { throw RuntimeException("$it") }
                        }.toNonEmptyList(),
                    ).getOrElse { throw RuntimeException("$it") },
                ),
            ),
        )
    }

    fun leggTilSaksbehandler(saksbehandler: NavIdentBruker.Saksbehandler): OpprettetRegulering =
        this.copy(
            saksbehandler = saksbehandler,
        )

    fun beregn(
        satsFactory: SatsFactory,
        begrunnelse: String? = null,
        clock: Clock,
    ): Either<KunneIkkeBeregneRegulering, OpprettetRegulering> {
        return this.utførBeregning(
            satsFactory = satsFactory,
            begrunnelse = begrunnelse,
            clock = clock,
        ).map { this.copy(beregning = it) }
    }

    fun simuler(
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Uføregrunnlag>?) -> Either<SimuleringFeilet, Simuleringsresultat>,
    ): Either<KunneIkkeSimulereRegulering, Pair<OpprettetRegulering, Utbetaling.SimulertUtbetaling>> {
        return simuler(
            beregning ?: return KunneIkkeSimulereRegulering.FantIngenBeregning.left(),
            when (sakstype) {
                Sakstype.ALDER -> {
                    null
                }

                Sakstype.UFØRE -> {
                    vilkårsvurderinger.uføreVilkår()
                        .getOrElse { throw IllegalStateException("Regulering uføre: $id mangler uføregrunnlag") }
                        .grunnlag
                        .toNonEmptyList()
                }
            },
        ).mapLeft { KunneIkkeSimulereRegulering.SimuleringFeilet }
            .map {
                when (it) {
                    is Simuleringsresultat.UtenForskjeller -> Pair(
                        copy(simulering = it.simulertUtbetaling.simulering),
                        it.simulertUtbetaling,
                    )

                    is Simuleringsresultat.MedForskjeller -> return KunneIkkeSimulereRegulering.Forskjeller(it.forskjeller)
                        .left()
                }
            }
    }

    fun avslutt(avsluttetAv: NavIdentBruker, clock: Clock): AvsluttetRegulering {
        return AvsluttetRegulering(
            opprettetRegulering = this,
            avsluttetTidspunkt = Tidspunkt.now(clock),
            avsluttetAv = avsluttetAv,
        )
    }

    fun tilIverksatt(): IverksattRegulering =
        IverksattRegulering(opprettetRegulering = this, beregning!!, simulering!!)

    /**
     * @return samler RuntimeException i en left.
     */
    private fun utførBeregning(
        satsFactory: SatsFactory,
        begrunnelse: String?,
        clock: Clock,
    ): Either<KunneIkkeBeregneRegulering.BeregningFeilet, Beregning> {
        return Either.catch {
            BeregningStrategyFactory(
                clock = clock,
                satsFactory = satsFactory,
            ).beregn(
                grunnlagsdataOgVilkårsvurderinger = this.grunnlagsdataOgVilkårsvurderinger,
                begrunnelse = begrunnelse,
                sakstype = this.sakstype,
            )
        }.mapLeft {
            KunneIkkeBeregneRegulering.BeregningFeilet(feil = it)
        }
    }
}
