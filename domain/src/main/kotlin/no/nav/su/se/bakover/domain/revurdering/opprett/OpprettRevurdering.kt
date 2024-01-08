package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.sak.nyRevurdering
import java.time.Clock
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent.Behandling.Revurdering.Opprettet as StatistikkEvent

/**
 * Tar ikke inn IO-funksjoner for å prøve holde opprett revurdering som en pure function.
 */
fun Sak.opprettRevurdering(
    command: OpprettRevurderingCommand,
    clock: Clock,
): Either<KunneIkkeOppretteRevurdering, OpprettRevurderingResultatUtenOppgaveId> {
    val informasjonSomRevurderes = InformasjonSomRevurderes.tryCreate(command.informasjonSomRevurderes)
        .getOrElse { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = command.periode,
        clock = clock,
    ).getOrElse { return KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(it).left() }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata)
        .onLeft { return KunneIkkeOppretteRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    val revurderingsårsak = command.revurderingsårsak.getOrElse {
        return KunneIkkeOppretteRevurdering.UgyldigRevurderingsårsak(it).left()
    }

    val tidspunkt = Tidspunkt.now(clock)
    return OpprettRevurderingResultatUtenOppgaveId(
        fnr = fnr,
        oppgaveConfig = {
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                aktørId = it,
                tilordnetRessurs = command.saksbehandler,
                clock = clock,
            )
        },
        opprettRevurdering = { oppgaveId ->
            OpprettetRevurdering(
                periode = command.periode,
                opprettet = tidspunkt,
                oppdatert = tidspunkt,
                tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = command.periode.fraOgMed)!!.id,
                vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
                saksbehandler = command.saksbehandler,
                oppgaveId = oppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                sakinfo = info(),
            )
        },
        sak = { nyRevurdering(it) },
        statistikkHendelse = { StatistikkEvent(it) },
    ).right()
}
