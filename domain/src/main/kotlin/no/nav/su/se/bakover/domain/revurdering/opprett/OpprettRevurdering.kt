package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.inneholderAlle
import no.nav.su.se.bakover.common.periode.måneder
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.periode
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.util.UUID
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
        .getOrElse { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = command.periode,
        clock = clock,
    ).getOrElse { return KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(it).left() }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata)
        .getOrElse { return KunneIkkeOppretteRevurdering.OpphørteVilkårMåRevurderes(it).left() }

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
            ).getOrElse {
                return KunneIkkeOppretteRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                    periode = uteståendeAvkorting.avkortingsvarsel.periode(),
                ).left()
            }
        },
    )

    unngåRevurderingAvPeriodeDetErPågåendeAvkortingFor(command.periode)
        .getOrElse {
            return KunneIkkeOppretteRevurdering.PågåendeAvkorting(it.periode, it.pågåendeAvkortingVedtakId).left()
        }

    val revurderingsårsak = command.revurderingsårsak.getOrElse {
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
                oppdatert = Tidspunkt.now(clock),
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

    data class PågåendeAvkorting(val periode: Periode, val vedtakId: UUID) : KunneIkkeOppretteRevurdering
}

/**
 * Unngå at man kan revurdere en periode dersom perioden tidligere har produsert et [no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel]
 * og en stønadsperiode har påbegynt avkortingen av dette. Tillater tilfeller hvor det ikke er overlapp mellom [periode] og avkortingsvarselet, samt tilfeller hvor
 * [periode] dekker både det aktuelle avkortingsvarsel og alle periodene med fradrag for avkorting i den nye stønadsperioden.
 */
fun Sak.unngåRevurderingAvPeriodeDetErPågåendeAvkortingFor(periode: Periode): Either<PågåendeAvkortingForPeriode, Unit> {
    val pågåendeAvkorting: List<Pair<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående>> =
        vedtakstidslinje()
            .tidslinje
            .map { it.originaltVedtak }
            .filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>()
            .map { it to it.behandling.avkorting }
            .filter { (_, avkorting) -> avkorting is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående }
            .filterIsInstance<Pair<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående>>()

    return if (pågåendeAvkorting.isEmpty()) {
        Unit.right()
    } else {
        pågåendeAvkorting.forEach { (vedtak, pågåendeAvkorting) ->
            if (periode.overlapper(pågåendeAvkorting.avkortingsvarsel.periode())) {
                val periodeSomMåOverlappes = (
                    pågåendeAvkorting.avkortingsvarsel.periode()
                        .måneder() + vedtak.behandling.grunnlagsdata.fradragsgrunnlag.filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                        .periode()
                    ).distinct().måneder()
                if (!periode.inneholderAlle(periodeSomMåOverlappes)) {
                    return PågåendeAvkortingForPeriode(
                        periode = periode,
                        pågåendeAvkortingVedtakId = vedtak.id,
                    ).left()
                }
            }
        }
        Unit.right()
    }
}

data class PågåendeAvkortingForPeriode(val periode: Periode, val pågåendeAvkortingVedtakId: UUID)
