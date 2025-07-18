package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.nyRevurdering
import org.slf4j.LoggerFactory
import java.time.Clock
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent.Behandling.Revurdering.Opprettet as StatistikkEvent

/**
 * Tar ikke inn IO-funksjoner for å prøve holde opprett revurdering som en pure function.
 */

private val log = LoggerFactory.getLogger("opprettRevurdering")

fun Sak.opprettRevurdering(
    command: OpprettRevurderingCommand,
    clock: Clock,
): Either<KunneIkkeOppretteRevurdering, OpprettRevurderingResultatUtenOppgaveId> {
    val informasjonSomRevurderes = InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(this.type, command.informasjonSomRevurderes)
        .getOrElse { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

    val revurderingsårsak = command.revurderingsårsak.getOrElse {
        return KunneIkkeOppretteRevurdering.UgyldigRevurderingsårsak(it).left()
    }
    // TODO: sjekk om
    if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN) {
        if (this.klager.none { it.erÅpen() }) {
            log.error("Fant ingen åpen klage for saksnummer ${this.saksnummer}, dette kan være fordi den er overført fra infotrygd hvis den gjelder alder. Ellers burde den finnes. Hør med fag.")
        }
        if (!command.omgjøringsgrunnErGyldig()) {
            return KunneIkkeOppretteRevurdering.MåhaOmgjøringsgrunn.left()
        }
    }

    val periode = command.periode
    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = periode,
        clock = clock,
    ).getOrElse {
        return KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(it).left()
    }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata.vilkårsvurderinger)
        .onLeft { return KunneIkkeOppretteRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    val tidspunkt = Tidspunkt.now(clock)
    return OpprettRevurderingResultatUtenOppgaveId(
        fnr = fnr,
        oppgaveConfig = {
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = command.saksbehandler,
                clock = clock,
            )
        },
        opprettRevurdering = { oppgaveId ->
            OpprettetRevurdering(
                periode = periode,
                opprettet = tidspunkt,
                oppdatert = tidspunkt,
                tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = periode.fraOgMed)!!.id,
                vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
                saksbehandler = command.saksbehandler,
                oppgaveId = oppgaveId,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                sakinfo = info(),
                omgjøringsgrunn = command.omgjøringsgrunn?.let { Omgjøringsgrunn.valueOf(command.omgjøringsgrunn) },
            )
        },
        sak = { nyRevurdering(it) },
        statistikkHendelse = { StatistikkEvent(it) },
    ).right()
}
