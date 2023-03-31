package no.nav.su.se.bakover.domain.visitor

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.firstOrThrowIfMultipleOrEmpty
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.visitors.RevurderingVisitor
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørtRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakVisitor
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

// TODO jah: Slett denne klassen og andre visitors og flytt logikken nærmere der den bør bo.
class LagBrevRequestVisitor(
    private val hentPerson: (fnr: Fnr) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHentePerson, Person>,
    private val hentNavn: (navIdentBruker: NavIdentBruker) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String>,
    private val hentGjeldendeUtbetaling: (sakId: UUID, forDato: LocalDate) -> Either<KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling, Int>,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : SøknadsbehandlingVisitor, RevurderingVisitor, VedtakVisitor {
    lateinit var brevRequest: Either<KunneIkkeLageBrevRequest, LagBrevRequest>

    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Uavklart) {
        throw KanIkkeLageBrevrequestForInstans(søknadsbehandling::class)
    }

    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Innvilget) {
        throw KanIkkeLageBrevrequestForInstans(søknadsbehandling::class)
    }

    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Avslag) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Avslag) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: SimulertSøknadsbehandling) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.MedBeregning) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.MedBeregning) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.MedBeregning) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: LukketSøknadsbehandling) {
        brevRequest = LagBrevRequestVisitor(hentPerson, hentNavn, hentGjeldendeUtbetaling, clock, satsFactory).let {
            søknadsbehandling.underliggendeSøknadsbehandling.accept(it)
            it.brevRequest
        }
    }

    override fun visit(revurdering: OpprettetRevurdering) {
        throw KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: BeregnetRevurdering.Innvilget) {
        throw KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: BeregnetRevurdering.Opphørt) {
        throw KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: SimulertRevurdering.Innvilget) {
        brevRequest = innvilgetRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
        )
    }

    override fun visit(revurdering: SimulertRevurdering.Opphørt) {
        brevRequest = opphørtRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            opphørsgrunner = revurdering.utledOpphørsgrunner(clock),
            simulering = revurdering.simulering,
        )
    }

    override fun visit(revurdering: RevurderingTilAttestering.Innvilget) {
        brevRequest = innvilgetRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
        )
    }

    override fun visit(revurdering: RevurderingTilAttestering.Opphørt) {
        brevRequest = opphørtRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            opphørsgrunner = revurdering.utledOpphørsgrunner(clock),
            simulering = revurdering.simulering,
        )
    }

    override fun visit(revurdering: IverksattRevurdering.Innvilget) {
        brevRequest = innvilgetRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
        )
    }

    override fun visit(revurdering: IverksattRevurdering.Opphørt) {
        brevRequest = opphørtRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            opphørsgrunner = revurdering.utledOpphørsgrunner(clock),
            simulering = revurdering.simulering,
        )
    }

    override fun visit(revurdering: UnderkjentRevurdering.Innvilget) {
        brevRequest = innvilgetRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
        )
    }

    override fun visit(revurdering: UnderkjentRevurdering.Opphørt) {
        brevRequest = opphørtRevurdering(
            revurdering = revurdering,
            beregning = revurdering.beregning,
            opphørsgrunner = revurdering.utledOpphørsgrunner(clock),
            simulering = revurdering.simulering,
        )
    }

    override fun visit(vedtak: VedtakInnvilgetSøknadsbehandling) {
        brevRequest = this.innvilgetSøknadsbehandling(
            søknadsbehandling = vedtak.behandling,
            beregning = vedtak.behandling.beregning,
        )
    }

    override fun visit(vedtak: VedtakInnvilgetRevurdering) {
        brevRequest = this.innvilgetRevurdering(
            revurdering = vedtak.behandling,
            beregning = vedtak.behandling.beregning,
            simulering = vedtak.behandling.simulering,
        )
    }

    override fun visit(vedtak: VedtakInnvilgetRegulering) {
        throw KanIkkeLageBrevrequestForInstans(vedtak::class)
    }

    override fun visit(vedtak: VedtakOpphørtRevurdering) {
        brevRequest = this.opphørtRevurdering(
            revurdering = vedtak.behandling,
            beregning = vedtak.behandling.beregning,
            opphørsgrunner = vedtak.behandling.utledOpphørsgrunner(clock),
            simulering = vedtak.simulering,
        )
    }

    override fun visit(vedtak: VedtakAvslagVilkår) {
        brevRequest = this.avslåttSøknadsbehandling(
            søknadsbehandling = vedtak.behandling,
            avslagsgrunner = vedtak.behandling.avslagsgrunner,
            beregning = null,
            fritekst = vedtak.behandling.fritekstTilBrev,
        )
    }

    override fun visit(vedtak: VedtakAvslagBeregning) {
        brevRequest = this.avslåttSøknadsbehandling(
            søknadsbehandling = vedtak.behandling,
            avslagsgrunner = vedtak.behandling.avslagsgrunner,
            beregning = vedtak.behandling.beregning,
            fritekst = vedtak.behandling.fritekstTilBrev,
        )
    }

    override fun visit(revurdering: AvsluttetRevurdering) {
        if (!revurdering.brevvalg.skalSendeBrev()) {
            throw IllegalArgumentException("Kan ikke lage brev for avsluttet revurdering ${revurdering.id} siden brevvalget tilsier at det ikke skal sendes brev.")
        }
        brevRequest = hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            // siden avslutt-brevet er et informasjons-brev, trengs ikke attestant
            attestant = null,
        ).map {
            LagBrevRequest.AvsluttRevurdering(
                person = it.person,
                fritekst = revurdering.brevvalg.fritekst,
                saksbehandlerNavn = it.saksbehandlerNavn,
                dagensDato = LocalDate.now(clock),
                saksnummer = revurdering.saksnummer,
            )
        }
    }

    override fun visit(vedtak: VedtakStansAvYtelse) {
        throw KanIkkeLageBrevrequestForInstans(vedtak::class)
    }

    override fun visit(vedtak: VedtakGjenopptakAvYtelse) {
        throw KanIkkeLageBrevrequestForInstans(vedtak::class)
    }

    private fun hentPersonOgNavn(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler?,
        attestant: NavIdentBruker.Attestant?,
    ): Either<KunneIkkeLageBrevRequest, PersonOgNavn> {
        return either.eager {
            val person = hentPerson(fnr).bind()
            val saksbehandlerNavn = saksbehandler?.let {
                hentNavn(it).bind()
            } ?: "-"

            val attestantNavn = attestant?.let {
                hentNavn(it).bind()
            } ?: "-"
            PersonOgNavn(
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
            )
        }
    }

    private fun avslåttSøknadsbehandling(
        søknadsbehandling: Søknadsbehandling,
        avslagsgrunner: List<Avslagsgrunn>,
        beregning: Beregning?,
        fritekst: String,
    ) =
        hentPersonOgNavn(
            fnr = søknadsbehandling.fnr,
            saksbehandler = søknadsbehandling.saksbehandler,
            attestant = søknadsbehandling.hentAttestantSomIverksatte(),
        ).map {
            requestForAvslag(
                personOgNavn = it,
                avslagsgrunner = avslagsgrunner,
                // Ved avslag så er det ikke sikkert bosituasjon er utfylt.
                harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.ifNotEmpty { harEPS() } ?: false,
                beregning = beregning,
                fritekst = fritekst,
                uføregrunnlag = søknadsbehandling.vilkårsvurderinger.hentUføregrunnlag(),
                formuevilkår = søknadsbehandling.vilkårsvurderinger.formue,
                saksnummer = søknadsbehandling.saksnummer,
                bosituasjon = søknadsbehandling.grunnlagsdata.bosituasjon,
                sakstype = søknadsbehandling.sakstype,
            )
        }

    private fun innvilgetSøknadsbehandling(søknadsbehandling: Søknadsbehandling, beregning: Beregning) =
        hentPersonOgNavn(
            fnr = søknadsbehandling.fnr,
            saksbehandler = søknadsbehandling.saksbehandler,
            attestant = søknadsbehandling.hentAttestantSomIverksatte(),
        ).map {
            requestForInnvilgelse(
                personOgNavn = it,
                // TODO("flere_satser denne må endres til å støtte flere")
                bosituasjon = søknadsbehandling.grunnlagsdata.bosituasjon,
                uføregrunnlag = søknadsbehandling.vilkårsvurderinger.hentUføregrunnlag(),
                beregning = beregning,
                fritekst = søknadsbehandling.fritekstTilBrev,
                saksnummer = søknadsbehandling.saksnummer,
                sakstype = søknadsbehandling.sakstype,
            )
        }

    private fun innvilgetRevurdering(
        revurdering: Revurdering,
        beregning: Beregning,
        simulering: Simulering,
    ): Either<KunneIkkeLageBrevRequest, LagBrevRequest> {
        return hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.hentAttestantSomIverksatte(),
        ).map {
            LagBrevRequest.Inntekt(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                attestantNavn = it.attestantNavn,
                revurdertBeregning = beregning,
                fritekst = if (revurdering.skalSendeVedtaksbrev()) {
                    revurdering.brevvalgRevurdering.skalSendeBrev()
                        .getOrElse { throw IllegalStateException("context mismatch: Revurderingen skal sende brev, men brevvalg skal ikke sendes. ${revurdering.id}") }.fritekst
                        ?: ""
                } else {
                    return KunneIkkeLageBrevRequest.SkalIkkeSendeBrev.left()
                },
                // TODO("flere_satser denne må endres til å støtte flere")
                harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
                forventetInntektStørreEnn0 = revurdering.vilkårsvurderinger.uføreVilkår()
                    .fold(
                        {
                            TODO("vilkårsvurdering_alder brev for alder er ikke implementert enda")
                        },
                        {
                            it.grunnlag.harForventetInntektStørreEnn0()
                        },
                    ),
                dagensDato = LocalDate.now(clock),
                saksnummer = revurdering.saksnummer,
                satsoversikt = Satsoversikt.fra(revurdering, satsFactory),
            ).let { innvilgetRevurdering ->
                if (revurdering.skalTilbakekreve()) {
                    LagBrevRequest.TilbakekrevingAvPenger(
                        ordinærtRevurderingBrev = innvilgetRevurdering,
                        tilbakekreving = Tilbakekreving(simulering.hentFeilutbetalteBeløp().månedbeløp),
                        satsoversikt = Satsoversikt.fra(revurdering, satsFactory),
                    )
                } else {
                    innvilgetRevurdering
                }
            }
        }
    }

    private fun Vilkårsvurderinger.hentUføregrunnlag(): List<Grunnlag.Uføregrunnlag> {
        return when (this) {
            is Vilkårsvurderinger.Revurdering.Uføre -> this.uføre.grunnlag
            is Vilkårsvurderinger.Søknadsbehandling.Uføre -> this.uføre.grunnlag
            is Vilkårsvurderinger.Revurdering.Alder -> TODO("vilkårsvurdering_alder brev for alder ikke implementert enda")
            is Vilkårsvurderinger.Søknadsbehandling.Alder -> emptyList() // TODO("vilkårsvurdering_alder brev for alder ikke implementert enda")
        }
    }

    private fun opphørtRevurdering(
        revurdering: Revurdering,
        beregning: Beregning,
        opphørsgrunner: List<Opphørsgrunn>,
        simulering: Simulering,
    ): Either<KunneIkkeLageBrevRequest, LagBrevRequest> {
        return hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.hentAttestantSomIverksatte(),
        ).map { personOgNavn ->
            // TODO avkorting refaktorer dette?
            val avkortingsbeløp = when (revurdering) {
                is BeregnetRevurdering.Opphørt -> {
                    when (revurdering.avkorting) {
                        is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
                            null
                        }
                    }
                }

                is IverksattRevurdering.Opphørt -> {
                    when (revurdering.avkorting) {
                        is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> {
                            null
                        }

                        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }

                        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }
                    }
                }

                is RevurderingTilAttestering.Opphørt -> {
                    when (revurdering.avkorting) {
                        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }

                        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }
                    }
                }

                is SimulertRevurdering.Opphørt -> {
                    when (revurdering.avkorting) {
                        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }

                        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }
                    }
                }

                is UnderkjentRevurdering.Opphørt -> {
                    when (revurdering.avkorting) {
                        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
                            null
                        }

                        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }

                        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                            revurdering.avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum()
                        }
                    }
                }

                else -> {
                    null
                }
            }

            LagBrevRequest.Opphørsvedtak(
                person = personOgNavn.person,
                harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
                beregning = beregning,
                fritekst = if (revurdering.skalSendeVedtaksbrev()) {
                    revurdering.brevvalgRevurdering.skalSendeBrev()
                        .getOrElse { throw IllegalStateException("context mismatch: Revurderingen skal sende brev, men brevvalg skal ikke sendes. ${revurdering.id}") }.fritekst
                        ?: ""
                } else {
                    return KunneIkkeLageBrevRequest.SkalIkkeSendeBrev.left()
                },
                saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
                attestantNavn = personOgNavn.attestantNavn,
                forventetInntektStørreEnn0 = revurdering.vilkårsvurderinger.hentUføregrunnlag()
                    .harForventetInntektStørreEnn0(),
                opphørsgrunner = opphørsgrunner,
                dagensDato = LocalDate.now(clock),
                saksnummer = revurdering.saksnummer,
                opphørsperiode = revurdering.periode,
                avkortingsBeløp = avkortingsbeløp,
                satsoversikt = Satsoversikt.fra(revurdering, satsFactory),
                // TODO("håndter_formue egentlig knyttet til formuegrenser")
                halvtGrunnbeløp = satsFactory.grunnbeløp(revurdering.periode.fraOgMed)
                    .halvtGrunnbeløpPerÅrAvrundet(),
            ).let { opphørsvedtak ->
                if (revurdering.skalTilbakekreve()) {
                    innvilgetRevurdering(
                        revurdering = revurdering,
                        beregning = beregning,
                        simulering = simulering,
                    ).getOrElse { throw RuntimeException(it.toString()) }
                } else {
                    opphørsvedtak
                }
            }
        }
    }

    private fun requestForAvslag(
        personOgNavn: PersonOgNavn,
        avslagsgrunner: List<Avslagsgrunn>,
        harEktefelle: Boolean,
        beregning: Beregning?,
        fritekst: String,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        formuevilkår: FormueVilkår,
        saksnummer: Saksnummer,
        bosituasjon: List<Grunnlag.Bosituasjon>,
        sakstype: Sakstype,
    ): LagBrevRequest.AvslagBrevRequest {
        val opprettet = Tidspunkt.now(clock)
        return LagBrevRequest.AvslagBrevRequest(
            person = personOgNavn.person,
            avslag = Avslag(
                opprettet = opprettet,
                avslagsgrunner = avslagsgrunner,
                harEktefelle = harEktefelle,
                beregning = beregning,
                formuegrunnlag = formuevilkår.hentFormueGrunnlagForSøknadsbehandling(avslagsgrunner),
                // TODO("håndter_formue egentlig knyttet til formuegrenser")
                halvtGrunnbeløpPerÅr = satsFactory.grunnbeløp(opprettet.toLocalDate(zoneIdOslo))
                    .halvtGrunnbeløpPerÅrAvrundet(),
            ),
            saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
            attestantNavn = personOgNavn.attestantNavn,
            fritekst = fritekst,
            forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0(),
            dagensDato = LocalDate.now(clock),
            saksnummer = saksnummer,
            // Ikke inkluder satsoversikt dersom beregning ikke er utført
            satsoversikt = beregning?.let { Satsoversikt.fra(bosituasjon, satsFactory, sakstype) },
            sakstype = sakstype,
        )
    }

    private fun requestForInnvilgelse(
        personOgNavn: PersonOgNavn,
        bosituasjon: List<Grunnlag.Bosituasjon>,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        beregning: Beregning,
        fritekst: String,
        saksnummer: Saksnummer,
        sakstype: Sakstype,
    ): LagBrevRequest.InnvilgetVedtak = LagBrevRequest.InnvilgetVedtak(
        person = personOgNavn.person,
        beregning = beregning,
        harEktefelle = bosituasjon.harEPS(),
        forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0(),
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
        fritekst = fritekst,
        dagensDato = LocalDate.now(clock),
        saksnummer = saksnummer,
        satsoversikt = Satsoversikt.fra(bosituasjon, satsFactory, sakstype),
        sakstype = sakstype,
    )

    private data class PersonOgNavn(
        val person: Person,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
    )

    data class KanIkkeLageBrevrequestForInstans(
        val instans: KClass<*>,
        val msg: String = "Kan ikke lage brevrequest for instans av typen: ${instans.qualifiedName}",
    ) : RuntimeException(msg)
}

private fun FormueVilkår.hentFormueGrunnlagForSøknadsbehandling(avslagsgrunner: List<Avslagsgrunn>): Formuegrunnlag? {
    return when (this) {
        is FormueVilkår.IkkeVurdert -> null
        // TODO(satsfactory_formue) jah: jeg har ikke endret funksjonaliteten i Sats-omskrivningsrunden, men hvorfor sjekker vi avslagsgrunn for å avgjøre dette? De burde jo uansett henge sammen.
        is FormueVilkår.Vurdert -> if (avslagsgrunner.contains(Avslagsgrunn.FORMUE)) this.grunnlag.firstOrThrowIfMultipleOrEmpty() else null
    }
}
