package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import java.time.Clock
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent.Behandling.Revurdering.Opprettet as StatistikkEvent

/**
 * Tar ikke inn IO-funksjoner for å prøve holde opprett revurdering som en pure function.
 */
fun Sak.opprettRevurdering(
    command: OpprettRevurderingCommand,
    clock: Clock,
): Either<KunneIkkeOppretteRevurdering, OpprettRevurderingResultatUtenOppgaveId> {
    if (harÅpenBehandling()) {
        return KunneIkkeOppretteRevurdering.HarÅpenBehandling.left()
    }

    val informasjonSomRevurderes = InformasjonSomRevurderes.tryCreate(command.informasjonSomRevurderes)
        .getOrHandle { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = command.periode,
        clock = clock,
    ).getOrHandle { return KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(it).left() }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata)
        .getOrHandle { return KunneIkkeOppretteRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    val uteståendeAvkorting = hentUteståendeAvkortingForRevurdering().fold(
        {
            it
        },
        { uteståendeAvkorting ->
            // TODO jah: Det fremstår som noe strengt at dersom det finnes en utestående avkorting, må vi revurdere den i sin helhet.
            //  Dersom vi ikke overlapper med periode, bør vi kanskje få lov til å revurdere?
            kontrollerAtUteståendeAvkortingRevurderes(
                periode = command.periode,
                uteståendeAvkorting = uteståendeAvkorting,
            ).getOrHandle {
                return KunneIkkeOppretteRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                    periode = uteståendeAvkorting.avkortingsvarsel.periode(),
                ).left()
            }
        },
    )

    val revurderingsårsak = command.revurderingsårsak.getOrHandle {
        return KunneIkkeOppretteRevurdering.UgyldigRevurderingsårsak(it).left()
    }

    return OpprettRevurderingResultatUtenOppgaveId(
        fnr = fnr,
        oppgaveConfig = {
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                aktørId = it,
                tilordnetRessurs = null,
                clock = clock,
            )
        },
        opprettRevurdering = { oppgaveId ->
            OpprettetRevurdering(
                periode = command.periode,
                opprettet = Tidspunkt.now(clock),
                tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = command.periode.fraOgMed)!!.id,
                saksbehandler = command.saksbehandler,
                oppgaveId = oppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = uteståendeAvkorting,
                sakinfo = info(),
            )
        },
        sak = {
            this.copy(revurderinger = this.revurderinger + it)
        },
        statistikkHendelse = { StatistikkEvent(it) },
    ).right()
}

sealed interface KunneIkkeOppretteRevurdering {

    object HarÅpenBehandling : KunneIkkeOppretteRevurdering

    object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppretteRevurdering

    data class VedtakInnenforValgtPeriodeKanIkkeRevurderes(
        val feil: Sak.GjeldendeVedtaksdataErUgyldigForRevurdering,
    ) : KunneIkkeOppretteRevurdering

    data class OpphørteVilkårMåRevurderes(val feil: Sak.OpphørtVilkårMåRevurderes) : KunneIkkeOppretteRevurdering

    data class UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
        val periode: Periode,
    ) : KunneIkkeOppretteRevurdering

    data class UgyldigRevurderingsårsak(
        val feil: Revurderingsårsak.UgyldigRevurderingsårsak,
    ) : KunneIkkeOppretteRevurdering

    data class FantIkkeAktørId(val feil: KunneIkkeHentePerson) : KunneIkkeOppretteRevurdering

    data class KunneIkkeOppretteOppgave(val feil: OppgaveFeil.KunneIkkeOppretteOppgave) : KunneIkkeOppretteRevurdering
}
