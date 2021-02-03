package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import java.time.Clock

class LagBrevRequestVisitor(
    private val hentPerson: (fnr: Fnr) -> Either<BrevRequestFeil, Person>,
    private val hentNavn: (navIdentBruker: NavIdentBruker) -> Either<BrevRequestFeil, String>,
    private val clock: Clock = Clock.systemUTC()
) : SøknadsbehandlingVisitor {
    lateinit var brevRequest: Either<BrevRequestFeil, LagBrevRequest>

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
        brevRequest = BrevRequestFeil.KunneIkkeLageBrevForStatus(søknadsbehandling.status).left()
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        brevRequest = BrevRequestFeil.KunneIkkeLageBrevForStatus(søknadsbehandling.status).left()
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

    private fun hentPersonOgNavn(søknadsbehandling: Søknadsbehandling): Either<BrevRequestFeil, PersonOgNavn> {
        return hentPerson(søknadsbehandling.fnr)
            .map { person ->
                val saksbehandlerVisitor = FinnSaksbehandlerVisitor().apply {
                    søknadsbehandling.accept(this)
                    saksbehandler?.let {
                        hentNavn(it).getOrHandle { return BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left() }
                    }
                }
                val attestantVisitor = FinnAttestantVisitor().apply {
                    søknadsbehandling.accept(this)
                    attestant?.let {
                        hentNavn(it).getOrHandle { return BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left() }
                    }
                }
                PersonOgNavn(
                    person = person,
                    saksbehandlerNavn = saksbehandlerVisitor.saksbehandler?.navIdent ?: "-",
                    attestantNavn = attestantVisitor.attestant?.navIdent ?: "-"
                )
            }
    }

    private fun avslag(
        søknadsbehandling: Søknadsbehandling,
        avslagsgrunner: List<Avslagsgrunn>,
        beregning: Beregning?
    ) =
        hentPersonOgNavn(søknadsbehandling)
            .map {
                requestForAvslag(
                    personOgNavn = it,
                    avslagsgrunner = avslagsgrunner,
                    behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                    beregning = beregning,
                )
            }

    private fun innvilget(søknadsbehandling: Søknadsbehandling, beregning: Beregning) =
        hentPersonOgNavn(søknadsbehandling)
            .map {
                requestForInnvilgelse(
                    personOgNavn = it,
                    behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                    beregning = beregning
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
        data class KunneIkkeLageBrevForStatus(val status: Behandling.BehandlingsStatus) : BrevRequestFeil()
    }
}
