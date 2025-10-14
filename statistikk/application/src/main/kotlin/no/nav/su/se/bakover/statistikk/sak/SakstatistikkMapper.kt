package no.nav.su.se.bakover.statistikk.sak

import behandling.domain.Behandling
import behandling.klage.domain.VurderingerTilKlage
import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.klage.toResultatBegrunnelse
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingResultat
import vilkår.common.domain.Avslagsgrunn
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal fun StatistikkEvent.Behandling.toBehandlingsstatistikkOverordnet(
    clock: Clock,
): SakStatistikk {
    return when (this) {
        is StatistikkEvent.Behandling.Søknad -> {
            when (this) {
                is StatistikkEvent.Behandling.Søknad.Opprettet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.name,
                    opprettetAv = søknadsbehandling.saksbehandler?.navIdent
                        ?: NavIdentBruker.Saksbehandler.systembruker().navIdent,
                    saksbehandler = søknadsbehandling.saksbehandler?.navIdent,
                )

                is StatistikkEvent.Behandling.Søknad.OpprettetOmgjøring -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.name,
                    opprettetAv = søknadsbehandling.saksbehandler?.navIdent
                        ?: NavIdentBruker.Saksbehandler.systembruker().navIdent,
                    saksbehandler = søknadsbehandling.saksbehandler?.navIdent,
                    behandlingAarsak = "Omgjøring etter avvist søknad",
                    relatertId = relatertId,
                )

                is StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.name,
                    behandlingResultat = BehandlingResultat.Innvilget.name,
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    utbetaltTid = søknadsbehandling.stønadsperiode.periode.fraOgMed,
                )

                is StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.name,
                    behandlingResultat = BehandlingResultat.Avvist.name,
                    resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.name,
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    behandlingResultat = BehandlingResultat.Innvilget.name,
                )

                is StatistikkEvent.Behandling.Søknad.Underkjent.Avslag -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.name,
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                )

                is StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    ferdigbehandletTid = vedtak.opprettet,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.name,
                    behandlingResultat = BehandlingResultat.Innvilget.name,
                    utbetaltTid = søknadsbehandling.periode.fraOgMed,
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                        ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                )

                is StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.name,
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    ferdigbehandletTid = vedtak.opprettet,
                    behandlingResultat = BehandlingResultat.Avvist.name,
                    resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                    ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                        ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                )

                is StatistikkEvent.Behandling.Søknad.Lukket -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.name, // TODO hvis avvist med vedtak skal det være iverksatt.. Men bør den ha annen hendelse enn Lukket?
                    behandlingResultat = søknadsbehandling.søknad.toBehandlingResultat().name,
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    ansvarligBeslutter = lukketAv.navIdent,
                )
            }
        }

        is StatistikkEvent.Behandling.Revurdering -> {
            when (this) {
                is StatistikkEvent.Behandling.Revurdering.Opprettet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.name,
                    opprettetAv = revurdering.saksbehandler.navIdent,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                )

                is StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.name,
                    behandlingResultat = BehandlingResultat.Innvilget.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                )

                is StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.name,
                    behandlingResultat = BehandlingResultat.Opphør.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingResultat = BehandlingResultat.Innvilget.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                )

                is StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Opphør.name,
                    resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    behandlingStatus = BehandlingStatus.Iverksatt.name,
                    saktype = revurdering.sakstype,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingResultat = BehandlingResultat.Innvilget.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    ansvarligBeslutter = revurdering.hentAttestantSomIverksatte()?.navIdent
                        ?: throw IllegalStateException("Et underkjent avslag kan ikke mangle attestant"),
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                )

                is StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Opphør.name,
                    resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Revurdering.Avsluttet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                )
            }
        }

        is StatistikkEvent.Behandling.Stans -> {
            when (this) {
                is StatistikkEvent.Behandling.Stans.Opprettet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    opprettetAv = revurdering.saksbehandler.navIdent,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingMetode = BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler),
                )

                is StatistikkEvent.Behandling.Stans.Avsluttet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Avbrutt.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingMetode = BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler),
                )

                is StatistikkEvent.Behandling.Stans.Iverksatt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Stanset.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingMetode = BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler),
                )
            }
        }

        is StatistikkEvent.Behandling.Gjenoppta -> {
            when (this) {
                is StatistikkEvent.Behandling.Gjenoppta.Opprettet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    opprettetAv = revurdering.saksbehandler.navIdent,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Gjenoppta.Avsluttet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Avbrutt.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Gjenoppta.Iverksatt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.name,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Gjenopptatt.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                )
            }
        }

        is StatistikkEvent.Behandling.Klage -> {
            when (this) {
                is StatistikkEvent.Behandling.Klage.Opprettet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.name,
                    opprettetAv = klage.saksbehandler.navIdent,
                    saksbehandler = klage.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Klage.Avsluttet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.name,
                    behandlingResultat = BehandlingResultat.Avbrutt.name,
                    saksbehandler = klage.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Klage.Avvist -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.name,
                    behandlingResultat = BehandlingResultat.Avvist.name,
                    resultatBegrunnelse = this.klage.vilkårsvurderinger.toResultatBegrunnelse(),
                    saksbehandler = klage.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Klage.Oversendt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.OversendtKlage.name,
                    behandlingResultat = BehandlingResultat.OpprettholdtKlage.name,
                    resultatBegrunnelse = (this.klage.vurderinger.vedtaksvurdering as? VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold)?.hjemler?.toResultatBegrunnelse(),
                    saksbehandler = klage.saksbehandler.navIdent,
                )

                // TODO is StatistikkEvent.Behandling.Klage.Medhold -> this.toBehandlingsstatistikkGenerell(...)
            }
        }

        // TODO Tilbakekreving ??
    }
}

private fun StatistikkEvent.Behandling.toBehandlingsstatistikkGenerell(
    clock: Clock,
    behandling: Behandling,
    behandlingType: Behandlingstype,
    saktype: Sakstype,
    behandlingStatus: String,
    opprettetAv: String? = null,
    relatertId: UUID? = null,
    behandlingAarsak: String? = null,
    omgjøringsårsak: String? = null,
    saksbehandler: String? = null,
    ferdigbehandletTid: Tidspunkt? = null,
    utbetaltTid: LocalDate? = null,
    behandlingResultat: String? = null,
    resultatBegrunnelse: String? = null,
    ansvarligBeslutter: String? = null,
    behandlingMetode: BehandlingMetode = BehandlingMetode.Manuell,

): SakStatistikk {
    return SakStatistikk(
        hendelseTid = Tidspunkt.now(clock),
        tekniskTid = Tidspunkt.now(clock),
        sakId = behandling.sakId,
        saksnummer = behandling.saksnummer.nummer,
        behandlingId = behandling.id.value,
        relatertBehandlingId = relatertId,
        aktorId = behandling.fnr,
        sakYtelse = saktype.toYtelseType().name,
        behandlingType = behandlingType.name,
        mottattTid = behandling.opprettet,
        registrertTid = behandling.opprettet,
        ferdigbehandletTid = ferdigbehandletTid,
        utbetaltTid = utbetaltTid,
        behandlingStatus = behandlingStatus,
        behandlingResultat = behandlingResultat,
        resultatBegrunnelse = resultatBegrunnelse,
        behandlingAarsak = omgjøringsårsak?.let {
            "$behandlingAarsak - $omgjøringsårsak"
        } ?: behandlingAarsak,
        opprettetAv = opprettetAv,
        saksbehandler = saksbehandler,
        ansvarligBeslutter = ansvarligBeslutter,
        behandlingMetode = behandlingMetode,
    )
}

private fun utledAvslagsgrunner(avslagsgrunner: List<Avslagsgrunn>): String? {
    return if (avslagsgrunner.isEmpty()) null else avslagsgrunner.joinToString(",")
}

private fun listUtOpphørsgrunner(opphørsgrunner: List<Opphørsgrunn>): String? {
    return if (opphørsgrunner.isEmpty()) null else opphørsgrunner.joinToString(",")
}
