package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
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
import java.time.Clock

class LagBrevRequestVisitor(
    private val hentPerson: (fnr: Fnr) -> Either<BrevRequestFeil, Person>,
    private val hentNavn: (navIdentBruker: NavIdentBruker) -> Either<BrevRequestFeil, String>,
    private val clock: Clock
) : SøknadsbehandlingVisitor, RevurderingVisitor {
    lateinit var brevRequest: Either<BrevRequestFeil, LagBrevRequest>

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
        throw BrevRequestFeil.KanIkkeLageBrevrequestForInstansException(søknadsbehandling)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        throw BrevRequestFeil.KanIkkeLageBrevrequestForInstansException(søknadsbehandling)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
        brevRequest = innvilget(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
        brevRequest = innvilget(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
        brevRequest = innvilget(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
        brevRequest = innvilget(søknadsbehandling, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, null)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) {
        brevRequest = avslag(søknadsbehandling, søknadsbehandling.avslagsgrunner, søknadsbehandling.beregning)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        brevRequest = innvilget(søknadsbehandling, søknadsbehandling.beregning)
    }

    private fun hentPersonOgNavn(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler?,
        attestant: NavIdentBruker.Attestant?
    ): Either<BrevRequestFeil, PersonOgNavn> {
        return hentPerson(fnr)
            .map { person ->
                PersonOgNavn(
                    person = person,
                    saksbehandlerNavn = saksbehandler?.let { saksbehandler ->
                        hentNavn(saksbehandler).fold(
                            ifLeft = { return BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left() },
                            ifRight = { it }
                        )
                    } ?: "-",
                    attestantNavn = attestant?.let { attestant ->
                        hentNavn(attestant).fold(
                            ifLeft = { return BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left() },
                            ifRight = { it }
                        )
                    } ?: "-"
                )
            }
    }

    private fun avslag(
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

    private fun innvilget(søknadsbehandling: Søknadsbehandling, beregning: Beregning) =
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

    private fun innvilgelze(revurdering: Revurdering, beregning: Beregning) =
        hentPersonOgNavn(
            fnr = revurdering.fnr,
            saksbehandler = revurdering.saksbehandler,
            attestant = no.nav.su.se.bakover.domain.revurdering.FinnAttestantVisitor().let {
                revurdering.accept(it)
                it.attestant
            }
        ).map {
            LagBrevRequest.Revurdering.Inntekt(
                person = it.person,
                saksbehandlerNavn = it.saksbehandlerNavn,
                revurdertBeregning = beregning,
                fritekst = null, // TODO: finn ut hvordan vi vill hantere fritekst
                vedtattBeregning = revurdering.tilRevurdering.beregning,
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

    sealed class BrevRequestFeil {
        object KunneIkkeHentePerson : BrevRequestFeil()
        object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : BrevRequestFeil()

        data class KanIkkeLageBrevrequestForInstansException(
            val instans: Any,
            val msg: String = "Kan ikke laga brevrequest for instans av typen: ${instans::class.qualifiedName}"
        ) : RuntimeException(msg)
    }

    override fun visit(revurdering: OpprettetRevurdering) {
        throw BrevRequestFeil.KanIkkeLageBrevrequestForInstansException(revurdering)
    }

    override fun visit(revurdering: BeregnetRevurdering) {
        throw BrevRequestFeil.KanIkkeLageBrevrequestForInstansException(revurdering)
    }

    override fun visit(revurdering: SimulertRevurdering) {
        brevRequest = innvilgelze(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: RevurderingTilAttestering) {
        brevRequest = innvilgelze(revurdering, revurdering.beregning)
    }

    override fun visit(revurdering: IverksattRevurdering) {
        brevRequest = innvilgelze(revurdering, revurdering.beregning)
    }
}
