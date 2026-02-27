package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.domain.BehandlingMedAttestering
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.attestering.UnderkjennAttesteringsgrunn
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.VurderingsperiodeUføre
import vilkår.vurderinger.domain.Grunnlagsdata
import økonomi.domain.simulering.Simulering
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

sealed class ReguleringUnderBehandling :
    Regulering,
    BehandlingMedAttestering {
    override fun erÅpen() = true
    override fun erAvsluttet() = false
    override fun erAvbrutt() = false
    override fun skalSendeVedtaksbrev() = false
    override val erFerdigstilt = false

    fun leggTilBeregningsgrunnlag(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradragsgrunnlag: List<Fradragsgrunnlag>,
        uføregrunnlag: List<Uføregrunnlag>,
        clock: Clock,
    ): Either<FeilMedBeregningsgrunnlag, ReguleringUnderBehandling> {
        sikkerLogg.debug(
            "Skal legge til {} og {} for regulering {}. Vilkår & grunnlag som er på behandling NÅ: {}, {}",
            fradragsgrunnlag,
            uføregrunnlag,
            this.id,
            grunnlagsdata,
            vilkårsvurderinger,
        )
        val oppdatertRegulering = Either.catch {
            val nyttFradrag = Grunnlagsdata.tryCreate(
                bosituasjon = grunnlagsdata.bosituasjonSomFullstendig(),
                fradragsgrunnlag = fradragsgrunnlag,
            ).getOrElse { throw IllegalStateException("Kunne ikke legge til fradrag ved regulering: $it") }

            val vilkårMedOppdatertUføre = if (uføregrunnlag.isNotEmpty()) {
                vilkårsvurderinger.oppdaterVilkår(
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
                )
            } else {
                null
            }

            val nyttGrunnlagOgvilkår = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                grunnlagsdata = nyttFradrag,
                vilkårsvurderinger = vilkårMedOppdatertUføre ?: vilkårsvurderinger,
            )
            when (this) {
                is OpprettetRegulering -> copy(
                    saksbehandler = saksbehandler,
                    grunnlagsdataOgVilkårsvurderinger = nyttGrunnlagOgvilkår,
                )

                is BeregnetRegulering -> copy(
                    saksbehandler = saksbehandler,
                    grunnlagsdataOgVilkårsvurderinger = nyttGrunnlagOgvilkår,
                )

                is TilAttestering -> throw IkkeEndreUnderAttestering()
            }
        }.getOrElse {
            return FeilMedBeregningsgrunnlag(it).left()
        }
        return oppdatertRegulering.right()
    }

    fun endreTilManuell(begrunnelse: String): ReguleringUnderBehandling {
        val reguleringstype = Reguleringstype.MANUELL(
            setOf(
                ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet(begrunnelse = begrunnelse),
            ),
        )
        return when (this) {
            is OpprettetRegulering -> copy(reguleringstype = reguleringstype)
            is BeregnetRegulering -> copy(reguleringstype = reguleringstype)
            is TilAttestering -> throw IkkeEndreUnderAttestering()
        }
    }

    // TODO AUTO-REG-26 Utgår?
    fun oppdaterMedSupplement(
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
            saksnummer = saksnummer,
        )
        return when (this) {
            is OpprettetRegulering -> this.copy(
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
            // TODO bjg denne skal vurderes
            else -> throw IllegalStateException("Kan kun legge til supplement behandling er beregnet")
        }
    }

    fun avslutt(avsluttetAv: NavIdentBruker, clock: Clock): AvsluttetRegulering {
        return AvsluttetRegulering(
            opprettetRegulering = this,
            avsluttetTidspunkt = Tidspunkt.now(clock),
            avsluttetAv = avsluttetAv,
        )
    }

    fun tilBeregnet(
        beregning: Beregning,
        simulering: Simulering,
    ) = BeregnetRegulering(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        saksbehandler = saksbehandler,
        reguleringstype = reguleringstype,
        sakstype = sakstype,
        eksternSupplementRegulering = eksternSupplementRegulering,
        beregning = beregning,
        simulering = simulering,
        attesteringer = attesteringer,
    )

    data class OpprettetRegulering(
        override val id: ReguleringId,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        override val periode: Periode = grunnlagsdataOgVilkårsvurderinger.periode()!!,
        override val beregning: Beregning? = null,
        override val simulering: Simulering? = null,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val reguleringstype: Reguleringstype,
        override val sakstype: Sakstype,
        override val eksternSupplementRegulering: EksternSupplementRegulering? = null,

        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    ) : ReguleringUnderBehandling()

    data class BeregnetRegulering(
        override val id: ReguleringId,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        override val periode: Periode = grunnlagsdataOgVilkårsvurderinger.periode()!!,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val reguleringstype: Reguleringstype,
        override val sakstype: Sakstype,
        override val eksternSupplementRegulering: EksternSupplementRegulering?,
        override val attesteringer: Attesteringshistorikk,
    ) : ReguleringUnderBehandling() {
        fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler) = TilAttestering(
            saksbehandler = saksbehandler,
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            reguleringstype = reguleringstype,
            sakstype = sakstype,
            eksternSupplementRegulering = eksternSupplementRegulering,
            beregning = beregning,
            simulering = simulering,
            attesteringer = attesteringer,
        )
    }

    data class TilAttestering(
        override val id: ReguleringId,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        override val periode: Periode = grunnlagsdataOgVilkårsvurderinger.periode()!!,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val reguleringstype: Reguleringstype,
        override val sakstype: Sakstype,
        override val eksternSupplementRegulering: EksternSupplementRegulering?,
        override val attesteringer: Attesteringshistorikk,
    ) : ReguleringUnderBehandling() {

        fun godkjenn(attestant: NavIdentBruker.Attestant, clock: Clock): IverksattRegulering {
            val godkjentRegulering = copy(
                attesteringer = attesteringer.leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = Tidspunkt.now(clock),
                    ),
                ),
            )
            return IverksattRegulering(godkjentRegulering, beregning, simulering)
        }

        fun underkjenn(
            attestant: NavIdentBruker.Attestant,
            kommentar: String,
            clock: Clock,
        ) = BeregnetRegulering(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            saksbehandler = saksbehandler,
            reguleringstype = reguleringstype,
            sakstype = sakstype,
            eksternSupplementRegulering = eksternSupplementRegulering,
            beregning = beregning,
            simulering = simulering,
            attesteringer = attesteringer.leggTilNyAttestering(
                Attestering.Underkjent(
                    attestant = attestant,
                    opprettet = Tidspunkt.now(clock),
                    grunn = UnderkjennelseGrunnRegulering.REGULERING_ER_FEIL,
                    kommentar = kommentar,
                ),
            ),
        )
    }
}

enum class UnderkjennelseGrunnRegulering : UnderkjennAttesteringsgrunn {
    REGULERING_ER_FEIL,
}

data class FeilMedBeregningsgrunnlag(
    val error: Throwable,
)

class IkkeEndreUnderAttestering : IllegalStateException("Skal ikke kunne endres under tilstand til attestering")
