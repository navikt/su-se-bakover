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
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
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
import java.time.Clock
import kotlin.reflect.KClass

class LagBrevRequestVisitor(
    private val hentPerson: (fnr: Fnr) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHentePerson, Person>,
    private val hentNavn: (navIdentBruker: NavIdentBruker) -> Either<KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String>,
    private val clock: Clock
) : SøknadsbehandlingVisitor, RevurderingVisitor, VedtakVisitor {
    lateinit var brevRequest: Either<KunneIkkeLageBrevRequest, LagBrevRequest>

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(søknadsbehandling::class)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(søknadsbehandling::class)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) {
        brevRequest = avslåttSøknadsbehandling(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        brevRequest = innvilgetSøknadsbehandling(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(revurdering: OpprettetRevurdering) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: BeregnetRevurdering) {
        throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(revurdering::class)
    }

    override fun visit(revurdering: SimulertRevurdering) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: RevurderingTilAttestering) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: IverksattRevurdering) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: UnderkjentRevurdering) {
        brevRequest = innvilgetRevurdering(revurdering, revurdering.beregning)
    }

    override fun visit(vedtak: Vedtak.InnvilgetStønad) {
        brevRequest = when (vedtak.behandling) {
            is Søknadsbehandling -> {
                innvilgetVedtakSøknadsbehandling(vedtak)
            }
            is Revurdering -> {
                innvilgetVedtakRevurdering(vedtak)
            }
            else -> throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(vedtak::class)
        }
    }

    override fun visit(vedtak: Vedtak.AvslåttStønad.MedBeregning) {
        brevRequest = when (vedtak.behandling) {
            is Søknadsbehandling -> {
                avslåttVedtakSøknadsbehandling(vedtak)
            }
            else -> throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(vedtak::class)
        }
    }

    override fun visit(vedtak: Vedtak.AvslåttStønad.UtenBeregning) {
        brevRequest = when (vedtak.behandling) {
            is Søknadsbehandling -> {
                avslåttVedtakSøknadsbehandling(vedtak)
            }
            else -> throw KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(vedtak::class)
        }
    }

    private fun hentPersonOgNavn(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler?,
        attestant: NavIdentBruker.Attestant?
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
                    } ?: "-"
                )
            }
    }

    private fun avslåttSøknadsbehandling(
        søknadsbehandling: Søknadsbehandling,
        avslagsgrunner: List<Avslagsgrunn>,
        beregning: Beregning?
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
            }
        ).map {
            requestForAvslag(
                personOgNavn = it,
                avslagsgrunner = avslagsgrunner,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                beregning = beregning,
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
            }
        ).map {
            requestForInnvilgelse(
                personOgNavn = it,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                beregning = beregning
            )
        }

    private fun innvilgetRevurdering(revurdering: Revurdering, beregning: Beregning) =
        hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = FinnAttestantVisitor().let {
                revurdering.accept(it)
                it.attestant
            }
        ).map {
            LagBrevRequest.Revurdering.Inntekt(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                revurdertBeregning = beregning,
                fritekst = null, // TODO: finn ut hvordan vi vill hantere fritekst
                harEktefelle = revurdering.tilRevurdering.behandlingsinformasjon.harEktefelle()
            )
        }

    private fun requestForAvslag(
        personOgNavn: PersonOgNavn,
        avslagsgrunner: List<Avslagsgrunn>,
        behandlingsinformasjon: Behandlingsinformasjon,
        beregning: Beregning?
    ): AvslagBrevRequest = AvslagBrevRequest(
        person = personOgNavn.person,
        avslag = Avslag(
            opprettet = Tidspunkt.now(clock),
            avslagsgrunner = avslagsgrunner,
            harEktefelle = behandlingsinformasjon.harEktefelle(),
            beregning = beregning
        ),
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn
    )

    private fun requestForInnvilgelse(
        personOgNavn: PersonOgNavn,
        behandlingsinformasjon: Behandlingsinformasjon,
        beregning: Beregning
    ): LagBrevRequest.InnvilgetVedtak = LagBrevRequest.InnvilgetVedtak(
        person = personOgNavn.person,
        beregning = beregning,
        behandlingsinformasjon = behandlingsinformasjon,
        saksbehandlerNavn = personOgNavn.saksbehandlerNavn,
        attestantNavn = personOgNavn.attestantNavn,
    )

    private data class PersonOgNavn(
        val person: Person,
        val saksbehandlerNavn: String,
        val attestantNavn: String
    )

    sealed class KunneIkkeLageBrevRequest {
        object KunneIkkeHentePerson : KunneIkkeLageBrevRequest()
        object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevRequest()

        data class KanIkkeLageBrevrequestForInstans(
            val instans: KClass<*>,
            val msg: String = "Kan ikke lage brevrequest for instans av typen: ${instans.qualifiedName}"
        ) : RuntimeException(msg)
    }

    private fun innvilgetVedtakSøknadsbehandling(vedtak: Vedtak.InnvilgetStønad) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant
        ).map {
            requestForInnvilgelse(
                personOgNavn = it,
                behandlingsinformasjon = vedtak.behandlingsinformasjon,
                beregning = vedtak.beregning
            )
        }

    private fun innvilgetVedtakRevurdering(vedtak: Vedtak.InnvilgetStønad) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant,
        ).map {
            LagBrevRequest.Revurdering.Inntekt(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                revurdertBeregning = vedtak.beregning,
                fritekst = null, // TODO: finn ut hvordan vi vill hantere fritekst
                harEktefelle = vedtak.behandlingsinformasjon.harEktefelle()
            )
        }

    private fun avslåttVedtakSøknadsbehandling(
        vedtak: Vedtak.AvslåttStønad
    ) =
        hentPersonOgNavn(
            fnr = vedtak.behandling.fnr,
            saksbehandler = vedtak.saksbehandler,
            attestant = vedtak.attestant
        ).map {
            requestForAvslag(
                personOgNavn = it,
                avslagsgrunner = vedtak.avslagsgrunner,
                behandlingsinformasjon = vedtak.behandlingsinformasjon,
                beregning = when (vedtak) {
                    is Vedtak.AvslåttStønad.MedBeregning -> vedtak.beregning
                    is Vedtak.AvslåttStønad.UtenBeregning -> null
                },
            )
        }
}
