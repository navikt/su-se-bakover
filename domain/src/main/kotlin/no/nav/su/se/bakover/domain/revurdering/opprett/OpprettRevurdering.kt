package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.FerdigstiltOmgjortKlage
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak.Årsak
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Tar ikke inn IO-funksjoner for å prøve holde opprett revurdering som en pure function.
 */

private val log = LoggerFactory.getLogger("opprettRevurdering")

fun Sak.opprettRevurdering(
    cmd: OpprettRevurderingCommand,
    clock: Clock,
): Either<KunneIkkeOppretteRevurdering, OpprettRevurderingResultat> {
    val informasjonSomRevurderes = InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(this.type, cmd.informasjonSomRevurderes)
        .getOrElse { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

    val revurderingsårsak = cmd.revurderingsårsak.getOrElse {
        return KunneIkkeOppretteRevurdering.UgyldigRevurderingsårsak(it).left()
    }

    if (revurderingsårsak.årsak.erOmgjøring()) {
        // TODO: gir denne mening fortsatt?
        if (revurderingsårsak.årsak == Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN) {
            if (this.klager.none { it.erÅpen() }) {
                log.error("Fant ingen åpen klage for saksnummer ${this.saksnummer}, dette kan være fordi den er overført fra infotrygd hvis den gjelder alder. Ellers burde den finnes. Hør med fag.")
            }
        }
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
        val klage = this.hentKlage(KlageId(klageId)) ?: return KunneIkkeOppretteRevurdering.KlageMåFinnesForKnytning.left()

        when (klage) {
            is FerdigstiltOmgjortKlage -> {
                if (klage.behandlingId != null) {
                    log.warn("Klage ${klage.id} er knyttet mot ${klage.behandlingId} fra før av. Sakid: $saksnummer")
                    return KunneIkkeOppretteRevurdering.KlageErAlleredeKnyttetTilBehandling.left()
                }
                val vedtaksvurdering = klage.vurderinger.vedtaksvurdering
                if (vedtaksvurdering.årsak.name != cmd.omgjøringsgrunn) {
                    log.warn("Klage ${klage.id} har grunn ${vedtaksvurdering.årsak.name} saksbehandler har valgt ${cmd.omgjøringsgrunn} Sakid: $saksnummer")
                    return KunneIkkeOppretteRevurdering.UlikOmgjøringsgrunn.left()
                }
            }
            else -> {
                log.error("Klage ${klage.id} er ikke FerdigstiltOmgjortKlage men ${klage.javaClass.name}. Dette skjer hvis saksbehandler ikke har ferdigstilt klagen. Sakid: $saksnummer")
                return KunneIkkeOppretteRevurdering.KlageErIkkeFerdigstilt.left()
            }
        }
        log.info("Knytter omgjøring mot klage ${klage.id} for saksnummer $saksnummer")
        klage.id
    } else {
        null
    }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata.vilkårsvurderinger)
        .onLeft { return KunneIkkeOppretteRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    return OpprettRevurderingResultat(
        gjeldendeVedtak = gjeldendeVedtak,
        gjeldendeVedtaksdata = gjeldendeVedtaksdata,
        revurderingsårsak = revurderingsårsak,
        informasjonSomRevurderes = informasjonSomRevurderes,
        klageId = relatertId as? KlageId,
    ).right()
}
