package no.nav.su.se.bakover.domain.visitor

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
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
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import no.nav.su.se.bakover.domain.vedtak.VedtakVisitor
import java.time.Clock
import kotlin.reflect.KClass

class LagBrevRequestVisitor(
    private val hentPerson: (fnr: Fnr) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHentePerson, Person>,
    private val hentNavn: (navIdentBruker: NavIdentBruker) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String>,
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

    override fun visit(vedtak: Vedtak.EndringIYtelse) {
        brevRequest = when (vedtak.vedtakType) {
            VedtakType.SØKNAD -> {
                innvilgetVedtakSøknadsbehandling(vedtak)
            }
            VedtakType.ENDRING -> {
                innvilgetVedtakRevurdering(vedtak)
            }
            VedtakType.OPPHØR -> {
                opphørsvedtak(vedtak)
            }
            VedtakType.AVSLAG,
            VedtakType.INGEN_ENDRING,
            -> {
                throw KunneIkkeLageBrevRequest.UgyldigKombinasjonAvVedtakOgTypeException(vedtak::class, vedtak.vedtakType)
            }
        }
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
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                beregning = beregning,
                fritekst = fritekst,
                uføregrunnlag = søknadsbehandling.grunnlagsdata.uføregrunnlag
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
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                beregning = beregning,
                fritekst = søknadsbehandling.fritekstTilBrev,
                uføregrunnlag = søknadsbehandling.grunnlagsdata.uføregrunnlag
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
        ).map {
            requestIngenEndring(
                personOgNavn = it,
                beregning = beregning,
                fritekst = revurdering.fritekstTilBrev,
                harEktefelle = revurdering.behandlingsinformasjon.harEktefelle(),
                uføregrunnlag = revurdering.grunnlagsdata.uføregrunnlag
            )
        }

    private fun requestIngenEndring(
        personOgNavn: PersonOgNavn,
        beregning: Beregning,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fritekst: String,
        harEktefelle: Boolean,
    ) = LagBrevRequest.VedtakIngenEndring(
        person = personOgNavn.person,
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
        beregning = beregning,
        fritekst = fritekst,
        harEktefelle = harEktefelle,
        forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0(),
    )

    private fun innvilgetRevurdering(revurdering: Revurdering, beregning: Beregning) =
        hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = FinnAttestantVisitor().let {
                revurdering.accept(it)
                it.attestant
            },
        ).map { it ->
            LagBrevRequest.Revurdering.Inntekt(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                attestantNavn = it.attestantNavn,
                revurdertBeregning = beregning,
                fritekst = revurdering.fritekstTilBrev,
                harEktefelle = revurdering.behandlingsinformasjon.harEktefelle(),
                forventetInntektStørreEnn0 = revurdering.grunnlagsdata.uføregrunnlag.harForventetInntektStørreEnn0(),
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
                behandlingsinformasjon = revurdering.behandlingsinformasjon,
                beregning = beregning,
                fritekst = revurdering.fritekstTilBrev,
                saksbehandlerNavn = it.saksbehandlerNavn,
                attestantNavn = it.attestantNavn,
                forventetInntektStørreEnn0 = revurdering.grunnlagsdata.uføregrunnlag.harForventetInntektStørreEnn0(),
                opphørsgrunner = opphørsgrunner,
            )
        }

    private fun requestForAvslag(
        personOgNavn: PersonOgNavn,
        avslagsgrunner: List<Avslagsgrunn>,
        behandlingsinformasjon: Behandlingsinformasjon,
        beregning: Beregning?,
        fritekst: String,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ) = LagBrevRequest.AvslagBrevRequest(
        person = personOgNavn.person,
        avslag = Avslag(
            opprettet = Tidspunkt.now(clock),
            avslagsgrunner = avslagsgrunner,
            harEktefelle = behandlingsinformasjon.harEktefelle(),
            beregning = beregning,
        ),
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
        fritekst = fritekst,
        forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0()
    )

    private fun requestForInnvilgelse(
        personOgNavn: PersonOgNavn,
        behandlingsinformasjon: Behandlingsinformasjon,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        beregning: Beregning,
        fritekst: String,
    ): LagBrevRequest.InnvilgetVedtak = LagBrevRequest.InnvilgetVedtak(
        person = personOgNavn.person,
        beregning = beregning,
        behandlingsinformasjon = behandlingsinformasjon,
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
        fritekst = fritekst,
        forventetInntektStørreEnn0 = uføregrunnlag.harForventetInntektStørreEnn0()
    )

    private data class PersonOgNavn(
        val person: Person,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
    )

    sealed class KunneIkkeLageBrevRequest {
        object KunneIkkeHentePerson : KunneIkkeLageBrevRequest()
        object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevRequest()

        data class KanIkkeLageBrevrequestForInstans(
            val instans: KClass<*>,
            val msg: String = "Kan ikke lage brevrequest for instans av typen: ${instans.qualifiedName}",
        ) : RuntimeException(msg)

        data class UgyldigKombinasjonAvVedtakOgTypeException(
            val instans: KClass<*>,
            val vedtakType: VedtakType,
            val msg: String = "Kombinasjon av ${instans.qualifiedName} og vedtakType: $vedtakType er ugyldig!",
        ) : RuntimeException(msg)
    }

    private fun innvilgetVedtakSøknadsbehandling(vedtak: Vedtak.EndringIYtelse) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant,
        ).map {
            requestForInnvilgelse(
                personOgNavn = it,
                behandlingsinformasjon = vedtak.behandlingsinformasjon,
                beregning = vedtak.beregning,
                fritekst =
                // TODO ia: kommer vi oss unna denne? Hadde kanskje gått dersom vi lagret de genererte brevene.
                // Gjelder også de andre metodene her inne som går på vedtak
                when (val b = vedtak.behandling) {
                    is Søknadsbehandling -> b.fritekstTilBrev
                    else -> ""
                },
                uføregrunnlag = vedtak.behandling.grunnlagsdata.uføregrunnlag
            )
        }

    private fun innvilgetVedtakRevurdering(vedtak: Vedtak.EndringIYtelse) =
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
                fritekst = when (val b = vedtak.behandling) {
                    is Revurdering -> b.fritekstTilBrev
                    else -> ""
                },
                harEktefelle = vedtak.behandlingsinformasjon.harEktefelle(),
                forventetInntektStørreEnn0 = vedtak.behandling.grunnlagsdata.uføregrunnlag.harForventetInntektStørreEnn0()
            )
        }

    private fun opphørsvedtak(vedtak: Vedtak.EndringIYtelse) =
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
                fritekst = when (val b = vedtak.behandling) {
                    is Revurdering -> b.fritekstTilBrev
                    else -> ""
                },
                behandlingsinformasjon = vedtak.behandlingsinformasjon,
                forventetInntektStørreEnn0 = vedtak.behandling.grunnlagsdata.uføregrunnlag.harForventetInntektStørreEnn0(),
                opphørsgrunner = vedtak.behandling.vilkårsvurderinger.utledOpphørsgrunner(),
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
                behandlingsinformasjon = vedtak.behandlingsinformasjon,
                beregning = when (vedtak) {
                    is Vedtak.Avslag.AvslagBeregning -> vedtak.beregning
                    is Vedtak.Avslag.AvslagVilkår -> null
                },
                fritekst = when (val b = vedtak.behandling) {
                    is Søknadsbehandling -> b.fritekstTilBrev // TODO ia: kommer vi oss unna denne?
                    else -> ""
                },
                uføregrunnlag = vedtak.behandling.grunnlagsdata.uføregrunnlag
            )
        }

    private fun vedtakIngenEndringIYtelse(vedtak: Vedtak.IngenEndringIYtelse): Either<KunneIkkeLageBrevRequest, LagBrevRequest> = hentPersonOgNavn(
        fnr = vedtak.behandling.fnr,
        saksbehandler = vedtak.saksbehandler,
        attestant = vedtak.attestant,
    ).map {
        requestIngenEndring(
            personOgNavn = it,
            beregning = vedtak.beregning,
            fritekst = when (val b = vedtak.behandling) {
                is Revurdering -> b.fritekstTilBrev
                else -> ""
            },
            harEktefelle = vedtak.behandlingsinformasjon.harEktefelle(),
            uføregrunnlag = vedtak.behandling.grunnlagsdata.uføregrunnlag
        )
    }
}
