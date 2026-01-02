import behandling.domain.Behandling
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.Hjemmel
import behandling.klage.domain.Klagehjemler
import behandling.klage.domain.VurderingerTilKlage
import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.Søknad
import vilkår.common.domain.Avslagsgrunn
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

enum class YtelseType {
    SUUFORE,
    SUALDER,
}

fun Sakstype.toYtelseType() = when (this) {
    Sakstype.ALDER -> YtelseType.SUALDER
    Sakstype.UFØRE -> YtelseType.SUUFORE
}

fun StatistikkEvent.Behandling.toBehandlingsstatistikkOverordnet(
    clock: Clock,
    førsteLinje: SakStatistikk? = null,
): SakStatistikk {
    return when (this) {
        is StatistikkEvent.Behandling.Søknad -> {
            when (this) {
                is StatistikkEvent.Behandling.Søknad.Opprettet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.toString(),
                    opprettetAv = søknadsbehandling.saksbehandler?.navIdent
                        ?: NavIdentBruker.Saksbehandler.systembruker().navIdent,
                    saksbehandler = søknadsbehandling.saksbehandler?.navIdent
                        ?: NavIdentBruker.Saksbehandler.systembruker().navIdent,
                )

                is StatistikkEvent.Behandling.Søknad.OpprettetOmgjøring -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Registrert.toString(),
                    opprettetAv = søknadsbehandling.saksbehandler?.navIdent
                        ?: NavIdentBruker.Saksbehandler.systembruker().navIdent,
                    saksbehandler = søknadsbehandling.saksbehandler?.navIdent,
                    behandlingAarsak = "OMGJORING_ETTER_AVVIST",
                    relatertId = relatertId,
                )

                is StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.toString(),
                    behandlingResultat = BehandlingResultat.Innvilget.toString(),
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    utbetaltTid = søknadsbehandling.stønadsperiode.periode.fraOgMed,
                    opprettetAv = førsteLinje?.opprettetAv,
                )

                is StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.toString(),
                    behandlingResultat = BehandlingResultat.Avslag.toString(),
                    resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    opprettetAv = førsteLinje?.opprettetAv,
                )

                is StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.toString(),
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    behandlingResultat = BehandlingResultat.Innvilget.toString(),
                    opprettetAv = førsteLinje?.opprettetAv,
                    ansvarligBeslutter = søknadsbehandling.hentAttestantSomUnderkjente()?.navIdent,
                )

                is StatistikkEvent.Behandling.Søknad.Underkjent.Avslag -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.toString(),
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                    opprettetAv = førsteLinje?.opprettetAv,
                    ansvarligBeslutter = søknadsbehandling.hentAttestantSomUnderkjente()?.navIdent,
                    behandlingResultat = BehandlingResultat.Avslag.toString(),
                )

                is StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    ferdigbehandletTid = vedtak.opprettet,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    behandlingResultat = BehandlingResultat.Innvilget.toString(),
                    utbetaltTid = søknadsbehandling.periode.fraOgMed,
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                        ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                    opprettetAv = førsteLinje?.opprettetAv,
                )

                is StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    ferdigbehandletTid = vedtak.opprettet,
                    behandlingResultat = BehandlingResultat.Avslag.toString(),
                    resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                    ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                        ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                    opprettetAv = førsteLinje?.opprettetAv,
                )

                is StatistikkEvent.Behandling.Søknad.Lukket -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = søknadsbehandling,
                    behandlingType = Behandlingstype.SOKNAD,
                    saktype = søknadsbehandling.sakstype,
                    behandlingResultat = søknadsbehandling.søknad.toBehandlingResultat().toString(),
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    ansvarligBeslutter = lukketAv.navIdent,
                    behandlingStatus = if (avslagTidligSøknad) {
                        BehandlingStatus.Iverksatt.toString()
                    } else {
                        BehandlingStatus.Avsluttet.toString()
                    },
                    resultatBegrunnelse = if (avslagTidligSøknad) "Avslag på grunn av for tidlig søknad" else "",
                    ferdigbehandletTid = søknadsbehandling.avsluttetTidspunkt,
                    opprettetAv = førsteLinje?.opprettetAv,
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
                    behandlingStatus = BehandlingStatus.Registrert.toString(),
                    opprettetAv = revurdering.saksbehandler.navIdent,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                    relatertId = relatertId,
                )

                is StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.toString(),
                    behandlingResultat = BehandlingResultat.Innvilget.toString(),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.TilAttestering.toString(),
                    behandlingResultat = BehandlingResultat.Opphør.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.toString(),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingResultat = BehandlingResultat.Innvilget.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    ansvarligBeslutter = revurdering.hentAttestantSomUnderkjente()?.navIdent,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Underkjent.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Opphør.toString(),
                    resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    ansvarligBeslutter = revurdering.hentAttestantSomUnderkjente()?.navIdent,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    saktype = revurdering.sakstype,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    ferdigbehandletTid = vedtak.opprettet,
                    behandlingResultat = BehandlingResultat.Innvilget.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    ansvarligBeslutter = revurdering.hentAttestantSomIverksatte()?.navIdent
                        ?: throw IllegalStateException("Et underkjent avslag kan ikke mangle attestant"),
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Opphør.toString(),
                    ansvarligBeslutter = revurdering.hentAttestantSomIverksatte()?.navIdent,
                    resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    ferdigbehandletTid = vedtak.opprettet,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Revurdering.Avsluttet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingResultat = BehandlingResultat.Avbrutt.toString(),
                    behandlingMetode = if (revurdering.revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                        BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler)
                    } else {
                        BehandlingMetode.Manuell
                    },
                    ferdigbehandletTid = revurdering.avsluttetTidspunkt,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
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
                    behandlingStatus = BehandlingStatus.Registrert.toString(),
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
                    behandlingStatus = BehandlingStatus.Avsluttet.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Avbrutt.toString(),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    behandlingMetode = BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler),
                    ferdigbehandletTid = revurdering.avsluttetTidspunkt,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Stans.Iverksatt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Stanset.toString(),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    ferdigbehandletTid = vedtak.opprettet,
                    behandlingMetode = BehandlingMetode.erAutomatiskHvisSystembruker(revurdering.saksbehandler),
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
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
                    behandlingStatus = BehandlingStatus.Registrert.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    opprettetAv = revurdering.saksbehandler.navIdent,
                    saksbehandler = revurdering.saksbehandler.navIdent,
                )

                is StatistikkEvent.Behandling.Gjenoppta.Avsluttet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Avbrutt.toString(),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    ferdigbehandletTid = revurdering.avsluttetTidspunkt,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Gjenoppta.Iverksatt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = revurdering,
                    behandlingType = Behandlingstype.REVURDERING,
                    saktype = revurdering.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    behandlingResultat = BehandlingResultat.Gjenopptatt.toString(),
                    saksbehandler = revurdering.saksbehandler.navIdent,
                    ferdigbehandletTid = vedtak.opprettet,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
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
                    behandlingStatus = BehandlingStatus.Registrert.toString(),
                    opprettetAv = klage.saksbehandler.navIdent,
                    saksbehandler = klage.saksbehandler.navIdent,
                    relatertId = relatertId,
                )

                is StatistikkEvent.Behandling.Klage.Avsluttet -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.Avsluttet.toString(),
                    behandlingResultat = BehandlingResultat.Avbrutt.toString(),
                    saksbehandler = klage.saksbehandler.navIdent,
                    ferdigbehandletTid = klage.avsluttetTidspunkt,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Klage.Avvist -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    behandlingResultat = BehandlingResultat.Avslag.toString(),
                    resultatBegrunnelse = this.klage.vilkårsvurderinger.toResultatBegrunnelse(),
                    saksbehandler = klage.saksbehandler.navIdent,
                    ferdigbehandletTid = vedtak.opprettet,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Klage.Oversendt -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.OversendtKlage.toString(),
                    behandlingResultat = when (this.klage.vurderinger) {
                        is VurderingerTilKlage.UtfyltOppretthold -> BehandlingResultat.OpprettholdtKlage.value
                        is VurderingerTilKlage.UtfyltDelvisOmgjøringKA -> BehandlingResultat.DelvisOmgjøringKa.value
                    },
                    resultatBegrunnelse = this.klage.vurderinger.vedtaksvurdering.hjemler.toResultatBegrunnelse(),
                    saksbehandler = klage.saksbehandler.navIdent,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )

                is StatistikkEvent.Behandling.Klage.FerdigstiltOmgjøring -> this.toBehandlingsstatistikkGenerell(
                    clock = clock,
                    behandling = klage,
                    behandlingType = Behandlingstype.KLAGE,
                    saktype = klage.sakstype,
                    behandlingStatus = BehandlingStatus.Iverksatt.toString(),
                    behandlingResultat = when (this.klage.vurderinger.vedtaksvurdering) {
                        is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Omgjør -> BehandlingResultat.OmgjortKlage.value
                        is VurderingerTilKlage.Vedtaksvurdering.Utfylt.DelvisOmgjøringEgenVedtaksinstans -> BehandlingResultat.DelvisOmgjøringEgenVedtaksinstans.value
                    },
                    resultatBegrunnelse = this.klage.vurderinger.vedtaksvurdering.årsak.name.uppercase(),
                    saksbehandler = klage.saksbehandler.navIdent,
                    ferdigbehandletTid = klage.datoklageferdigstilt,
                    opprettetAv = førsteLinje?.opprettetAv,
                    relatertId = førsteLinje?.relatertBehandlingId,
                )
            }
        }
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
        funksjonellTid = Tidspunkt.now(clock),
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

internal fun Søknad.Journalført.MedOppgave.Lukket.toBehandlingResultat(): BehandlingResultat {
    return when (this) {
        is Søknad.Journalført.MedOppgave.Lukket.Avvist -> BehandlingResultat.Avslag
        is Søknad.Journalført.MedOppgave.Lukket.Bortfalt -> BehandlingResultat.Bortfalt
        is Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker -> BehandlingResultat.Trukket
    }
}

internal fun FormkravTilKlage?.toResultatBegrunnelse(): String? {
    if (this == null) return null
    return listOf(
        if (this.innenforFristen?.svar == FormkravTilKlage.Svarord.NEI) "IKKE_INNENFOR_FRISTEN" else null,
        if (this.klagesDetPåKonkreteElementerIVedtaket?.svar == false) "KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET" else null,
        if (this.erUnderskrevet?.svar == FormkravTilKlage.Svarord.NEI) "IKKE_UNDERSKREVET" else null,
    ).mapNotNull { it }.joinToString(",").ifEmpty { null }
}

internal fun Klagehjemler.toResultatBegrunnelse(): String {
    return this.sorted().joinToString(",") { it.toJsonformat() }
}

private fun Hjemmel.toJsonformat(): String {
    return when (this) {
        Hjemmel.SU_PARAGRAF_3 -> "SU_PARAGRAF_3"
        Hjemmel.SU_PARAGRAF_4 -> "SU_PARAGRAF_4"
        Hjemmel.SU_PARAGRAF_5 -> "SU_PARAGRAF_5"
        Hjemmel.SU_PARAGRAF_6 -> "SU_PARAGRAF_6"
        Hjemmel.SU_PARAGRAF_7 -> "SU_PARAGRAF_7"
        Hjemmel.SU_PARAGRAF_8 -> "SU_PARAGRAF_8"
        Hjemmel.SU_PARAGRAF_9 -> "SU_PARAGRAF_9"
        Hjemmel.SU_PARAGRAF_10 -> "SU_PARAGRAF_10"
        Hjemmel.SU_PARAGRAF_11 -> "SU_PARAGRAF_11"
        Hjemmel.SU_PARAGRAF_12 -> "SU_PARAGRAF_12"
        Hjemmel.SU_PARAGRAF_13 -> "SU_PARAGRAF_13"
        Hjemmel.SU_PARAGRAF_17 -> "SU_PARAGRAF_17"
        Hjemmel.SU_PARAGRAF_18 -> "SU_PARAGRAF_18"
        Hjemmel.SU_PARAGRAF_21 -> "SU_PARAGRAF_21"
        Hjemmel.SU_PARAGRAF_22 -> "SU_PARAGRAF_22"
        Hjemmel.FVL_PARAGRAF_12 -> "FVL_PARAGRAF_12"
        Hjemmel.FVL_PARAGRAF_28 -> "FVL_PARAGRAF_28"
        Hjemmel.FVL_PARAGRAF_29 -> "FVL_PARAGRAF_29"
        Hjemmel.FVL_PARAGRAF_31 -> "FVL_PARAGRAF_31"
        Hjemmel.FVL_PARAGRAF_32 -> "FVL_PARAGRAF_32"
    }
}
