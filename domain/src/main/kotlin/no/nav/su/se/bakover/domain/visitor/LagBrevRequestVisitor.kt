package no.nav.su.se.bakover.domain.visitor

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.zip
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.behandling.satsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.harEktefelle
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.RevurderingVisitor
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.FinnSaksbehandlerVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingVisitor
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakVisitor
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

class LagBrevRequestVisitor(
    private val hentPerson: (fnr: Fnr) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHentePerson, Person>,
    private val hentNavn: (navIdentBruker: NavIdentBruker) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String>,
    private val hentGjeldendeUtbetaling: (sakId: UUID, forDato: LocalDate) -> Either<KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling, Int>,
    private val clock: Clock,
) : SøknadsbehandlingVisitor, RevurderingVisitor, VedtakVisitor {
    lateinit var brevRequest: Either<KunneIkkeLageBrevRequest, LagBrevRequest>

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(søknadsbehandling::class)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(søknadsbehandling::class)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(
            søknadsbehandling,
            søknadsbehandling.avslagsgrunner,
            null,
            søknadsbehandling.fritekstTilBrev,
        )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) {
        brevRequest =
            avslåttSøknadsbehandling(
                søknadsbehandling,
                søknadsbehandling.avslagsgrunner,
                søknadsbehandling.beregning,
                søknadsbehandling.fritekstTilBrev,
            )
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(revurdering: OpprettetRevurdering) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: BeregnetRevurdering.Innvilget) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: BeregnetRevurdering.Opphørt) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: BeregnetRevurdering.IngenEndring) {
        brevRequest = revurderingIngenEndring(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: SimulertRevurdering.Innvilget) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: SimulertRevurdering.Opphørt) {
        brevRequest = opphørtRevurdering(revurdering, revurdering.beregning, revurdering.utledOpphørsgrunner())
    }

    override fun visit(revurdering: RevurderingTilAttestering.Innvilget) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: RevurderingTilAttestering.Opphørt) {
        brevRequest = opphørtRevurdering(revurdering, revurdering.beregning, revurdering.utledOpphørsgrunner())
    }

    override fun visit(revurdering: RevurderingTilAttestering.IngenEndring) {
        brevRequest = revurderingIngenEndring(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: IverksattRevurdering.Innvilget) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: IverksattRevurdering.Opphørt) {
        brevRequest = opphørtRevurdering(revurdering, revurdering.beregning, revurdering.utledOpphørsgrunner())
    }

    override fun visit(revurdering: IverksattRevurdering.IngenEndring) {
        brevRequest = revurderingIngenEndring(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: UnderkjentRevurdering.Innvilget) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: UnderkjentRevurdering.Opphørt) {
        brevRequest = opphørtRevurdering(revurdering, revurdering.beregning, revurdering.utledOpphørsgrunner())
    }

    override fun visit(revurdering: UnderkjentRevurdering.IngenEndring) {
        brevRequest = revurderingIngenEndring(revurdering, revurdering.beregning)
    }

    override fun visit(vedtak: Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling) {
        brevRequest = innvilgetVedtakSøknadsbehandling(vedtak)
    }

    override fun visit(vedtak: Vedtak.EndringIYtelse.InnvilgetRevurdering) {
        brevRequest = innvilgetVedtakRevurdering(vedtak)
    }

    override fun visit(vedtak: Vedtak.EndringIYtelse.OpphørtRevurdering) {
        brevRequest = opphørsvedtak(vedtak)
    }

    override fun visit(vedtak: Vedtak.Avslag.AvslagVilkår) {
        brevRequest = avslåttVedtakSøknadsbehandling(vedtak)
    }

    override fun visit(vedtak: Vedtak.Avslag.AvslagBeregning) {
        brevRequest = avslåttVedtakSøknadsbehandling(vedtak)
    }

    override fun visit(vedtak: Vedtak.IngenEndringIYtelse) {
        brevRequest = vedtakIngenEndringIYtelse(vedtak)
    }

    override fun visit(vedtak: Vedtak.StansAvYtelse) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(vedtak::class)
    }

    private fun hentPersonOgNavn(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler?,
        attestant: NavIdentBruker.Attestant?,
    ): Either<KunneIkkeLageBrevRequest, PersonOgNavn> {
        return hentPerson(fnr)
            .map { person ->
                PersonOgNavn(
                    person = person,
                    saksbehandlerNavn = saksbehandler?.let { saksbehandler ->
                        hentNavn(saksbehandler).getOrElse {
                            return KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
                        }
                    } ?: "-",
                    attestantNavn = attestant?.let { attestant ->
                        hentNavn(attestant).getOrElse {
                            return KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
                        }
                    } ?: "-",
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
            saksbehandler = FinnSaksbehandlerVisitor().let {
                søknadsbehandling.accept(it)
                it.saksbehandler
            },
            attestant = FinnAttestantVisitor().let {
                søknadsbehandling.accept(it)
                it.attestant
            },
        ).map {
            requestForAvslag(
                personOgNavn = it,
                avslagsgrunner = avslagsgrunner,
                // Ved avslag så er det ikke sikkert bosituasjon er utfylt.
                harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.ifNotEmpty { harEktefelle() } ?: false,
                beregning = beregning,
                fritekst = fritekst,
                uføregrunnlag = søknadsbehandling.vilkårsvurderinger.uføre.grunnlag,
            )
        }

    private fun innvilgetSøknadsbehandling(søknadsbehandling: Søknadsbehandling, beregning: Beregning) =
        hentPersonOgNavn(
            fnr = søknadsbehandling.fnr,
            saksbehandler = FinnSaksbehandlerVisitor().let {
                søknadsbehandling.accept(it)
                it.saksbehandler
            },
            attestant = FinnAttestantVisitor().let {
                søknadsbehandling.accept(it)
                it.attestant
            },
        ).map {
            requestForInnvilgelse(
                personOgNavn = it,
                bosituasjon = søknadsbehandling.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
                beregning = beregning,
                fritekst = søknadsbehandling.fritekstTilBrev,
                uføregrunnlag = søknadsbehandling.vilkårsvurderinger.uføre.grunnlag,
            )
        }

    private fun innvilgetRevurdering(revurdering: Revurdering, beregning: Beregning) =
        hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = FinnAttestantVisitor().let {
                revurdering.accept(it)
                it.attestant
            },
        ).map {
            LagBrevRequest.Revurdering.Inntekt(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                attestantNavn = it.attestantNavn,
                revurdertBeregning = beregning,
                fritekst = revurdering.fritekstTilBrev,
                harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEktefelle(),
                forventetInntektStørreEnn0 = revurdering.vilkårsvurderinger.uføre.grunnlag.harForventetInntektStørreEnn0(),
            )
        }

    private fun opphørtRevurdering(revurdering: Revurdering, beregning: Beregning, opphørsgrunner: List<Opphørsgrunn>) =
        hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = FinnAttestantVisitor().let {
                revurdering.accept(it)
                it.attestant
            },
        ).map {
            LagBrevRequest.Opphørsvedtak(
                person = it.person,
                harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEktefelle(),
                beregning = beregning,
                fritekst = revurdering.fritekstTilBrev,
                saksbehandlerNavn = it.saksbehandlerNavn,
                attestantNavn = it.attestantNavn,
                forventetInntektStørreEnn0 = revurdering.vilkårsvurderinger.uføre.grunnlag.harForventetInntektStørreEnn0(),
                opphørsgrunner = opphørsgrunner,
            )
        }

    private fun requestForAvslag(
        personOgNavn: PersonOgNavn,
        avslagsgrunner: List<Avslagsgrunn>,
        harEktefelle: Boolean,
        beregning: Beregning?,
        fritekst: String,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ) = LagBrevRequest.AvslagBrevRequest(
        person = personOgNavn.person,
        avslag = Avslag(
            opprettet = Tidspunkt.now(clock),
            avslagsgrunner = avslagsgrunner,
            harEktefelle = harEktefelle,
            beregning = beregning,
        ),
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
        fritekst = fritekst,
        forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0(),
    )

    private fun requestForInnvilgelse(
        personOgNavn: PersonOgNavn,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        beregning: Beregning,
        fritekst: String,
    ): LagBrevRequest.InnvilgetVedtak = LagBrevRequest.InnvilgetVedtak(
        person = personOgNavn.person,
        beregning = beregning,
        satsgrunn = bosituasjon.satsgrunn(),
        harEktefelle = bosituasjon.harEktefelle(),
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
        fritekst = fritekst,
        forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0(),
    )

    private data class PersonOgNavn(
        val person: Person,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
    )

    sealed class KunneIkkeLageBrevRequest {
        object KunneIkkeHentePerson : KunneIkkeLageBrevRequest()
        object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevRequest()
        object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevRequest()

        data class KanIkkeLageBrevrequestForInstans(
            val instans: KClass<*>,
            val msg: String = "Kan ikke lage brevrequest for instans av typen: ${instans.qualifiedName}",
        ) : RuntimeException(msg)
    }

    private fun innvilgetVedtakSøknadsbehandling(vedtak: Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant,
        ).map {
            requestForInnvilgelse(
                personOgNavn = it,
                bosituasjon = vedtak.behandling.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
                beregning = vedtak.beregning,
                fritekst = vedtak.behandling.fritekstTilBrev,
                uføregrunnlag = vedtak.behandling.vilkårsvurderinger.uføre.grunnlag,
            )
        }

    private fun innvilgetVedtakRevurdering(vedtak: Vedtak.EndringIYtelse.InnvilgetRevurdering) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant,
        ).map {
            LagBrevRequest.Revurdering.Inntekt(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                attestantNavn = it.attestantNavn,
                revurdertBeregning = vedtak.beregning,
                fritekst = vedtak.behandling.fritekstTilBrev,
                harEktefelle = vedtak.behandling.grunnlagsdata.bosituasjon.harEktefelle(),
                forventetInntektStørreEnn0 = vedtak.behandling.vilkårsvurderinger.uføre.grunnlag.harForventetInntektStørreEnn0(),
            )
        }

    private fun opphørsvedtak(vedtak: Vedtak.EndringIYtelse.OpphørtRevurdering) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant,
        ).map {
            LagBrevRequest.Opphørsvedtak(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                attestantNavn = it.attestantNavn,
                beregning = vedtak.beregning,
                fritekst = vedtak.behandling.fritekstTilBrev,
                harEktefelle = vedtak.behandling.grunnlagsdata.bosituasjon.harEktefelle(),
                forventetInntektStørreEnn0 = vedtak.behandling.vilkårsvurderinger.uføre.grunnlag.harForventetInntektStørreEnn0(),
                opphørsgrunner = vedtak.utledOpphørsgrunner(),
            )
        }

    private fun avslåttVedtakSøknadsbehandling(
        vedtak: Vedtak.Avslag,
    ) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant,
        ).map {
            requestForAvslag(
                personOgNavn = it,
                avslagsgrunner = vedtak.avslagsgrunner,
                harEktefelle = vedtak.behandling.grunnlagsdata.bosituasjon.ifNotEmpty { harEktefelle() } ?: false,
                beregning = when (vedtak) {
                    is Vedtak.Avslag.AvslagBeregning -> vedtak.beregning
                    is Vedtak.Avslag.AvslagVilkår -> null
                },
                fritekst = when (vedtak) {
                    is Vedtak.Avslag.AvslagBeregning -> vedtak.behandling.fritekstTilBrev
                    is Vedtak.Avslag.AvslagVilkår -> vedtak.behandling.fritekstTilBrev
                },
                uføregrunnlag = vedtak.behandling.vilkårsvurderinger.uføre.grunnlag,
            )
        }

    private fun revurderingIngenEndring(revurdering: Revurdering, beregning: Beregning) =
        hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = FinnAttestantVisitor().let {
                revurdering.accept(it)
                it.attestant
            },
        )
            .zip(hentGjeldendeUtbetaling(revurdering.sakId, LocalDate.now(clock)))
            .map { (personOgNavn, gjeldendeUtbetaling) ->
                requestIngenEndring(
                    personOgNavn = personOgNavn,
                    beregning = beregning,
                    fritekst = revurdering.fritekstTilBrev,
                    harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEktefelle(),
                    uføregrunnlag = revurdering.vilkårsvurderinger.uføre.grunnlag,
                    gjeldendeMånedsutbetaling = gjeldendeUtbetaling,
                )
            }

    private fun vedtakIngenEndringIYtelse(vedtak: Vedtak.IngenEndringIYtelse): Either<KunneIkkeLageBrevRequest, LagBrevRequest.VedtakIngenEndring> =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant,
        )
            .zip(hentGjeldendeUtbetaling(vedtak.behandling.sakId, vedtak.opprettet.toLocalDate(zoneIdOslo)))
            .map { (personOgNavn, gjeldendeUtbetaling) ->
                requestIngenEndring(
                    personOgNavn = personOgNavn,
                    beregning = vedtak.beregning,
                    fritekst = vedtak.behandling.fritekstTilBrev,
                    harEktefelle = vedtak.behandling.grunnlagsdata.bosituasjon.harEktefelle(),
                    uføregrunnlag = vedtak.behandling.vilkårsvurderinger.uføre.grunnlag,
                    gjeldendeMånedsutbetaling = gjeldendeUtbetaling,
                )
            }

    private fun requestIngenEndring(
        personOgNavn: PersonOgNavn,
        beregning: Beregning,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fritekst: String,
        harEktefelle: Boolean,
        gjeldendeMånedsutbetaling: Int,
    ) = LagBrevRequest.VedtakIngenEndring(
        person = personOgNavn.person,
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
        beregning = beregning,
        fritekst = fritekst,
        harEktefelle = harEktefelle,
        forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0(),
        gjeldendeMånedsutbetaling = gjeldendeMånedsutbetaling,
    )
}
