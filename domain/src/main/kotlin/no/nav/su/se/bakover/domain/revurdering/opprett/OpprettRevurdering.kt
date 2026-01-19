package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.FerdigstiltOmgjortKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak.Årsak
import no.nav.su.se.bakover.domain.sak.nyRevurdering
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Tar ikke inn IO-funksjoner for å prøve holde opprett revurdering som en pure function.
 */

private val log = LoggerFactory.getLogger("opprettRevurdering")

fun Sak.kanOppretteRevurdering(
    cmd: OpprettRevurderingCommand,
    clock: Clock,
): Either<KunneIkkeOppretteRevurdering, KanOppretteRevurderingResultatData> {
    val informasjonSomRevurderes = InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(this.type, cmd.informasjonSomRevurderes)
        .getOrElse { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

    val revurderingsårsak = cmd.revurderingsårsak.getOrElse {
        return KunneIkkeOppretteRevurdering.UgyldigRevurderingsårsak(it).left()
    }

    if (revurderingsårsak.årsak.erOmgjøring()) {
        if (!cmd.omgjøringsgrunnErGyldig()) {
            return KunneIkkeOppretteRevurdering.MåhaOmgjøringsgrunn.left()
        }
    }

    val periode = cmd.periode
    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = periode,
        clock = clock,
    ).getOrElse {
        return KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(it).left()
    }
    val gjeldendeVedtak = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = periode.fraOgMed)!!
    val relatertId = if (revurderingsårsak.årsak.erOmgjøring() && revurderingsårsak.årsak == Årsak.OMGJØRING_EGET_TILTAK) {
        gjeldendeVedtak.behandling.id
    } else if (revurderingsårsak.årsak.erOmgjøring() && revurderingsårsak.årsak != Årsak.OMGJØRING_EGET_TILTAK) {
        val klageId = cmd.klageId?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: return KunneIkkeOppretteRevurdering.KlageUgyldigUUID.left()
        val klage =
            this.hentKlage(KlageId(klageId)) ?: return KunneIkkeOppretteRevurdering.KlageMåFinnesForKnytning.left()
        finnRelatertIdOmgjøringKlage(klage, revurderingsårsak, sakId = this.id, cmd.omgjøringsgrunn).getOrElse(
            { return it.left() },
        )
    } else {
        log.warn("Fant ingen id å knytte revurdering mot i sakid: $id årsak: ${revurderingsårsak.årsak} gjeldende vedtak: ${gjeldendeVedtak.id}")
        null
    }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata.vilkårsvurderinger)
        .onLeft { return KunneIkkeOppretteRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    return KanOppretteRevurderingResultatData(
        gjeldendeVedtak = gjeldendeVedtak,
        gjeldendeVedtaksdata = gjeldendeVedtaksdata,
        revurderingsårsak = revurderingsårsak,
        informasjonSomRevurderes = informasjonSomRevurderes,
        klageId = relatertId as? KlageId,
    ).right()
}

internal fun finnRelatertIdOmgjøringKlage(
    klage: Klage,
    revurderingsårsak: Revurderingsårsak,
    sakId: UUID,
    omgjøringsGrunn: String?,
): Either<KunneIkkeOppretteRevurdering, KlageId> {
    return when (revurderingsårsak.årsak) {
        // startNySøknadsbehandlingForAvslag( skal ikke ha denne logikken?
        Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN -> {
            when (klage) {
                is OversendtKlage -> {
                    if (klage.klageinstanshendelser.isNotEmpty()) {
                        if (klage.klageinstanshendelser.any { it is ProsessertKlageinstanshendelse.AvsluttetKaMedUtfall }) {
                            log.info("Fant hendelse fra KA på klage ${klage.id} som er avsluttet, får opprette omgjøring.")
                            klage.id.right()
                        } else {
                            log.error("Klage ${klage.id} er oversendt men har ingen avsluttede klagehendelser fra KABAL. Sakid: $sakId")
                            KunneIkkeOppretteRevurdering.IngenAvsluttedeKlageHendelserFraKA.left()
                        }
                    } else {
                        log.error("Klage ${klage.id} er oversendt men har ingen klagehendelser fra KABAL. Sakid: $sakId")
                        KunneIkkeOppretteRevurdering.IngenKlageHendelserFraKA.left()
                    }
                }
                else -> {
                    log.error("OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN -> Klage ${klage.id} er ikke OversendtKlage men ${klage.javaClass.name}. Dette skjer hvis saksbehandler ikke har oversendt klagen. Sakid: $sakId")
                    KunneIkkeOppretteRevurdering.KlageErIkkeOversendt.left()
                }
            }
        }

        Årsak.OMGJØRING_KLAGE -> {
            when (klage) {
                is FerdigstiltOmgjortKlage -> {
                    if (klage.behandlingId != null) {
                        log.warn("Klage ${klage.id} er knyttet mot ${klage.behandlingId} fra før av. Sakid: $sakId")
                        return KunneIkkeOppretteRevurdering.KlageErAlleredeKnyttetTilBehandling.left()
                    }
                    val vedtaksvurdering = klage.vurderinger.vedtaksvurdering
                    if (vedtaksvurdering.årsak.name != omgjøringsGrunn) {
                        log.warn("Klage ${klage.id} har grunn ${vedtaksvurdering.årsak.name} saksbehandler har valgt $omgjøringsGrunn Sakid: $sakId")
                        return KunneIkkeOppretteRevurdering.UlikOmgjøringsgrunn.left()
                    }
                    log.info("Knytter omgjøring mot klage ${klage.id} for sakid $sakId")
                    klage.id.right()
                }
                else -> {
                    log.error("Klage ${klage.id} er ikke FerdigstiltOmgjortKlage men ${klage.javaClass.name}. Dette skjer hvis saksbehandler ikke har ferdigstilt klagen. Sakid: $sakId")
                    KunneIkkeOppretteRevurdering.KlageErIkkeFerdigstiltOmgjortKlage.left()
                }
            }
        }

        Årsak.OMGJØRING_TRYGDERETTEN -> {
            when (klage) {
                is OversendtKlage -> {
                    if (klage.klageinstanshendelser.isNotEmpty()) {
                        if (klage.klageinstanshendelser.any { it is ProsessertKlageinstanshendelse.AnkeITrygderettenAvsluttet }) {
                            klage.id.right()
                        } else {
                            log.error("Klage ${klage.id} er oversendt men har ingen ankeITrygderettenAvsluttet fra KABAL. Må ha mottatt hendelse for å vite at behandlingen i KABAL er ferdig før vi omgjør. Sakid: $sakId")
                            KunneIkkeOppretteRevurdering.IngenTrygderettenAvsluttetHendelser.left()
                        }
                    } else {
                        log.error("Klage ${klage.id} er oversendt men har ingen klagehendelser fra KABAL. Sakid: $sakId")
                        KunneIkkeOppretteRevurdering.IngenKlageHendelserFraKA.left()
                    }
                }
                else -> {
                    log.error("OMGJØRING_TRYGDERETTEN -> Klage ${klage.id} er ikke OversendtKlage men ${klage.javaClass.name}. Dette skjer hvis saksbehandler ikke har ferdigstilt klagen. Sakid: $sakId")
                    KunneIkkeOppretteRevurdering.KlageErIkkeFerdigstilt.left()
                }
            }
        }

        else -> {
            log.error("feil årsak $revurderingsårsak for sakid $sakId ved omgjøring")
            KunneIkkeOppretteRevurdering.UgyldigRevurderingsårsak(Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak).left()
        }
    }
}

fun Sak.opprettRevurdering(kanOppretteRevurderingResultatData: KanOppretteRevurderingResultatData, oppgaveId: OppgaveId, tidspunkt: Tidspunkt, command: OpprettRevurderingCommand): Pair<Sak, OpprettetRevurdering> {
    val revurdering = OpprettetRevurdering(
        periode = command.periode,
        opprettet = tidspunkt,
        oppdatert = tidspunkt,
        tilRevurdering = kanOppretteRevurderingResultatData.gjeldendeVedtak.id,
        vedtakSomRevurderesMånedsvis = kanOppretteRevurderingResultatData.gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
        saksbehandler = command.saksbehandler,
        oppgaveId = oppgaveId,
        revurderingsårsak = kanOppretteRevurderingResultatData.revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger = kanOppretteRevurderingResultatData.gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
        informasjonSomRevurderes = kanOppretteRevurderingResultatData.informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        sakinfo = this.info(),
        omgjøringsgrunn = command.omgjøringsgrunn?.let { Omgjøringsgrunn.valueOf(it) },
    )
    val oppdatertSak = nyRevurdering(revurdering)
    return Pair(oppdatertSak, revurdering)
}
