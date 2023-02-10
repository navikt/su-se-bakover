package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørsperiodeForUtbetalinger
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.visitors.RevurderingVisitor
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.time.Clock
import java.util.UUID

sealed class BeregnetRevurdering : Revurdering() {
    abstract override val beregning: Beregning
    abstract override val avkorting: AvkortingVedRevurdering.DelvisHåndtert

    override fun skalTilbakekreve() = false

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    override fun erÅpen() = true

    override fun oppdater(
        clock: Clock,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return oppdaterInternal(
            clock = clock,
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            tilRevurdering = tilRevurdering,
            avkorting = avkorting,
            saksbehandler = saksbehandler,
        )
    }

    override fun skalSendeBrev() = !årsakErGRegulering() && brevvalgRevurdering.skalSendeBrev().isRight()

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering = BrevvalgRevurdering.IkkeValgt,
    ) : BeregnetRevurdering() {
        override val erOpphørt = false
        override val simulering: Simulering? = null

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun simuler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock,
            simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
        ): Either<SimulerUtbetalingFeilet, SimulertRevurdering.Innvilget> {
            return simuler(
                beregning,
                when (sakstype) {
                    Sakstype.ALDER -> {
                        null
                    }

                    Sakstype.UFØRE -> {
                        vilkårsvurderinger.uføreVilkår()
                            .getOrElse { throw IllegalStateException("Revurdering uføre: $id mangler uføregrunnlag") }
                            .grunnlag
                            .toNonEmptyList()
                    }
                },
            ).mapLeft {
                it
            }.map {
                val tilbakekrevingsbehandling = when (it.harFeilutbetalinger()) {
                    true -> {
                        IkkeAvgjort(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(clock),
                            sakId = sakId,
                            revurderingId = id,
                            periode = periode,
                        )
                    }

                    false -> {
                        IkkeBehovForTilbakekrevingUnderBehandling
                    }
                }

                SimulertRevurdering.Innvilget(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    oppdatert = oppdatert,
                    tilRevurdering = tilRevurdering,
                    vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                    beregning = beregning,
                    simulering = it,
                    saksbehandler = saksbehandler,
                    oppgaveId = oppgaveId,
                    revurderingsårsak = revurderingsårsak,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    avkorting = avkorting.håndter(),
                    tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                    sakinfo = sakinfo,
                    brevvalgRevurdering = brevvalgRevurdering,
                )
            }
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val oppdatert: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
        override val sakinfo: SakInfo,
        override val brevvalgRevurdering: BrevvalgRevurdering = BrevvalgRevurdering.IkkeValgt,
    ) : BeregnetRevurdering() {
        override val erOpphørt = true
        override val simulering: Simulering? = null

        fun simuler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock,
            simuler: (opphørsperiode: Periode, saksbehandler: NavIdentBruker.Saksbehandler) -> Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling>,
        ): Either<SimulerUtbetalingFeilet, SimulertRevurdering.Opphørt> {
            val (simulertUtbetaling, håndtertAvkorting) = simuler(periode, saksbehandler)
                .getOrElse { return it.left() }
                .let { simulering ->
                    when (val avkortingsvarsel = lagAvkortingsvarsel(simulering, clock)) {
                        is Avkortingsvarsel.Ingen -> {
                            simulering to when (avkorting) {
                                is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
                                    avkorting.håndter()
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
                                    avkorting.håndter()
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
                                    throw IllegalStateException("Skal ikke kunne skje")
                                }
                            }
                        }

                        is Avkortingsvarsel.Utenlandsopphold -> {
                            val nyOpphørsperiode = OpphørsperiodeForUtbetalinger(
                                revurdering = this,
                                avkortingsvarsel = avkortingsvarsel,
                            ).getOrElse { return SimulerUtbetalingFeilet.Avkorting(it).left() }.value
                            val simuleringMedNyOpphørsdato = simuler(nyOpphørsperiode, saksbehandler)
                                .getOrElse { return it.left() }

                            if (simuleringMedNyOpphørsdato.simulering.harFeilutbetalinger()) {
                                sikkerLogg.error(
                                    "Simulering: ${objectMapper.writeValueAsString(simuleringMedNyOpphørsdato.simulering)}",
                                )
                                throw IllegalStateException("Simulering med justert opphørsdato for utbetalinger pga avkorting utenlandsopphold inneholder feilutbetaling, se sikkerlogg for detaljer")
                            }

                            simuleringMedNyOpphørsdato to when (avkorting) {
                                is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
                                    avkorting.håndter(avkortingsvarsel as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes)
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
                                    avkorting.håndter(avkortingsvarsel as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes)
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
                                    throw IllegalStateException("Skal ikke kunne skje")
                                }
                            }
                        }
                    }
                }

            val tilbakekrevingsbehandling = when (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                true -> {
                    IkkeAvgjort(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        sakId = sakId,
                        revurderingId = id,
                        periode = periode,
                    )
                }

                false -> {
                    IkkeBehovForTilbakekrevingUnderBehandling
                }
            }

            unngåNyAvkortingOgNyTilbakekrevingPåSammeTid(
                avkorting = håndtertAvkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
            )

            return SimulertRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning,
                simulering = simulertUtbetaling.simulering,
                saksbehandler = saksbehandler,
                oppgaveId = oppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = håndtertAvkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            ).right()
        }

        private fun unngåNyAvkortingOgNyTilbakekrevingPåSammeTid(
            avkorting: AvkortingVedRevurdering.Håndtert,
            tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        ) {
            val førerTilAvkorting = when (avkorting) {
                is AvkortingVedRevurdering.Håndtert.AnnullerUtestående,
                AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres,
                -> {
                    false
                }

                is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel,
                is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående,
                -> {
                    true
                }
            }
            val måBehandleTilbakekreving = when (tilbakekrevingsbehandling) {
                is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
                    false
                }

                is IkkeTilbakekrev,
                is Tilbakekrev,
                is IkkeAvgjort,
                -> {
                    true
                }
            }
            if (førerTilAvkorting && måBehandleTilbakekreving) throw IllegalStateException("Kan ikke håndtere avkorting og tilbakekreving på samme tid.")
        }

        private fun lagAvkortingsvarsel(
            simulertUtbetaling: Utbetaling.SimulertUtbetaling,
            clock: Clock,
        ): Avkortingsvarsel {
            return when (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                true -> {
                    when (val vilkårsvurdering = vilkårsvurderinger.vurdering) {
                        is Vilkårsvurderingsresultat.Avslag -> {
                            when (vilkårsvurdering.erNøyaktigÅrsak(Inngangsvilkår.Utenlandsopphold)) {
                                true -> {
                                    Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                        sakId = this.sakId,
                                        revurderingId = this.id,
                                        simulering = simulertUtbetaling.simulering,
                                        opprettet = Tidspunkt.now(clock),
                                    ).skalAvkortes()
                                }

                                false -> {
                                    Avkortingsvarsel.Ingen
                                }
                            }
                        }

                        is Vilkårsvurderingsresultat.Innvilget -> {
                            Avkortingsvarsel.Ingen
                        }

                        is Vilkårsvurderingsresultat.Uavklart -> {
                            throw IllegalStateException("Kan ikke vurdere avkorting før vilkår er avklart.")
                        }
                    }
                }

                else -> {
                    Avkortingsvarsel.Ingen
                }
            }
        }

        fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            return when (
                val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                    vilkårsvurderinger = vilkårsvurderinger,
                    beregning = beregning,
                    clock = clock,
                ).resultat
            ) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun opphørSkyldesVilkår(): Boolean {
            return VurderOpphørVedRevurdering.Vilkårsvurderinger(vilkårsvurderinger).resultat is OpphørVedRevurdering.Ja
        }
    }
}
