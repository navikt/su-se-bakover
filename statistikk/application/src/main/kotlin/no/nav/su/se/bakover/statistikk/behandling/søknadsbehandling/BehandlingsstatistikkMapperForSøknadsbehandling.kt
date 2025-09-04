package no.nav.su.se.bakover.statistikk.behandling.søknadsbehandling

import behandling.domain.Stønadsbehandling
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.BehandlingsstatistikkDto
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import no.nav.su.se.bakover.statistikk.behandling.behandlingYtelseDetaljer
import no.nav.su.se.bakover.statistikk.behandling.mottattDato
import no.nav.su.se.bakover.statistikk.behandling.toBehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.toFunksjonellTid
import no.nav.su.se.bakover.statistikk.sak.toYtelseType
import statistikk.domain.SakStatistikk
import vilkår.common.domain.Avslagsgrunn
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal fun StatistikkEvent.Behandling.Omgjøring.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    when (this) {
        is StatistikkEvent.Behandling.Omgjøring.AvslåttOmgjøring ->
            return BehandlingsstatistikkDto(
                behandlingType = Behandlingstype.OMGJØRING_AVSLAG,
                behandlingTypeBeskrivelse = Behandlingstype.OMGJØRING_AVSLAG.beskrivelse,
                funksjonellTid = søknadsbehandling.opprettet,
                tekniskTid = Tidspunkt.now(clock),
                registrertDato = søknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
                mottattDato = LocalDate.now(clock),
                behandlingId = søknadsbehandling.id.value,
                sakId = søknadsbehandling.sakId,
                søknadId = søknadsbehandling.søknad.id,
                saksnummer = søknadsbehandling.saksnummer.nummer,
                versjon = gitCommit?.value,
                avsluttet = false,
                saksbehandler = saksbehandler.toString(),
                beslutter = null,
                behandlingYtelseDetaljer = søknadsbehandling.behandlingYtelseDetaljer(),
                behandlingStatus = BehandlingStatus.Registrert.name,
                behandlingStatusBeskrivelse = BehandlingStatus.Registrert.beskrivelse,
                resultat = this.søknadsbehandling.omgjøringsårsak?.name,
                resultatBeskrivelse = null,
                resultatBegrunnelse = null,
                totrinnsbehandling = true,
                ytelseType = this.søknadsbehandling.sakstype.toYtelseType(),
                omgjøringsgrunn = this.søknadsbehandling.omgjøringsgrunn?.name,
            )
    }
}

internal fun StatistikkEvent.Behandling.Søknad.toBehandlingsstatistikkDto(
    gitCommit: GitCommit?,
    clock: Clock,
): BehandlingsstatistikkDto {
    return when (this) {
        is StatistikkEvent.Behandling.Søknad.Opprettet -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.opprettet,
            behandlingStatus = BehandlingStatus.UnderBehandling,
            behandlingsresultat = null,
            resultatBegrunnelse = null,
            beslutter = null,
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            // Her lagres det ikke noe mer nøyaktig.
            funksjonellTid = Tidspunkt.now(clock),
            behandlingStatus = BehandlingStatus.TilAttestering,
            behandlingsresultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            beslutter = null,
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            // Her lagres det ikke noe mer nøyaktig.
            funksjonellTid = Tidspunkt.now(clock),
            behandlingStatus = BehandlingStatus.TilAttestering,
            behandlingsresultat = BehandlingResultat.AvslåttSøknadsbehandling,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
            beslutter = null,
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(
                this.søknadsbehandling.id.value,
                clock,
            ),
            behandlingStatus = BehandlingStatus.Underkjent,
            behandlingsresultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            beslutter = this.søknadsbehandling.prøvHentSisteAttestant(),
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Underkjent.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.søknadsbehandling.attesteringer.toFunksjonellTid(
                this.søknadsbehandling.id.value,
                clock,
            ),
            behandlingStatus = BehandlingStatus.Underkjent,
            behandlingsresultat = BehandlingResultat.AvslåttSøknadsbehandling,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
            beslutter = this.søknadsbehandling.prøvHentSisteAttestant(),
            totrinnsbehandling = true,
            avsluttet = false,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.vedtak.opprettet,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingsresultat = BehandlingResultat.Innvilget,
            resultatBegrunnelse = null,
            beslutter = this.vedtak.attestant,
            totrinnsbehandling = true,
            avsluttet = true,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag -> toDto(
            clock = clock,
            gitCommit = gitCommit,
            funksjonellTid = this.vedtak.opprettet,
            behandlingStatus = BehandlingStatus.Iverksatt,
            behandlingsresultat = BehandlingResultat.AvslåttSøknadsbehandling,
            resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
            beslutter = this.vedtak.attestant,
            totrinnsbehandling = true,
            avsluttet = true,
            saksbehandler = this.søknadsbehandling.saksbehandler,
        )

        is StatistikkEvent.Behandling.Søknad.Lukket -> {
            toDto(
                clock = clock,
                gitCommit = gitCommit,
                funksjonellTid = this.søknadsbehandling.lukketTidspunkt,
                behandlingStatus = BehandlingStatus.Avsluttet,
                behandlingsresultat = this.søknadsbehandling.toBehandlingResultat(),
                resultatBegrunnelse = null,
                // Det kreves ikke attestant ved lukking.
                beslutter = null,
                totrinnsbehandling = false,
                avsluttet = true,
                saksbehandler = this.saksbehandler,
            )
        }
    }
}

private fun StatistikkEvent.Behandling.Søknad.toDto(
    clock: Clock,
    gitCommit: GitCommit?,
    funksjonellTid: Tidspunkt,
    behandlingStatus: BehandlingStatus,
    behandlingsresultat: BehandlingResultat?,
    resultatBegrunnelse: String?,
    beslutter: NavIdentBruker.Attestant?,
    totrinnsbehandling: Boolean,
    avsluttet: Boolean,
    saksbehandler: NavIdentBruker.Saksbehandler?,
): BehandlingsstatistikkDto {
    val søknadsbehandling = this.søknadsbehandling
    val søknad = søknadsbehandling.søknad
    return BehandlingsstatistikkDto(
        behandlingType = Behandlingstype.SOKNAD,
        behandlingTypeBeskrivelse = Behandlingstype.SOKNAD.beskrivelse,
        funksjonellTid = funksjonellTid,
        tekniskTid = Tidspunkt.now(clock),
        // registrertDato skal samsvare med REGISTRERT-hendelsen sin funksjonellTid (som er når søknaden ble registrert i systemet vårt)
        registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
        mottattDato = søknad.mottattDato(),
        behandlingId = søknadsbehandling.id.value,
        sakId = søknadsbehandling.sakId,
        søknadId = søknadsbehandling.søknad.id,
        saksnummer = søknadsbehandling.saksnummer.nummer,
        versjon = gitCommit?.value,
        avsluttet = avsluttet,
        saksbehandler = saksbehandler?.toString(),
        beslutter = beslutter?.toString(),
        behandlingYtelseDetaljer = søknadsbehandling.behandlingYtelseDetaljer(),
        behandlingStatus = behandlingStatus.toString(),
        behandlingStatusBeskrivelse = behandlingStatus.beskrivelse,
        resultat = behandlingsresultat?.toString(),
        resultatBeskrivelse = behandlingsresultat?.beskrivelse,
        resultatBegrunnelse = resultatBegrunnelse,
        totrinnsbehandling = totrinnsbehandling,
        ytelseType = this.søknadsbehandling.sakstype.toYtelseType(),
    )
}

private fun utledAvslagsgrunner(avslagsgrunner: List<Avslagsgrunn>): String? {
    return if (avslagsgrunner.isEmpty()) null else avslagsgrunner.joinToString(",")
}

internal fun StatistikkEvent.Behandling.toBehandlingsstatistikkOverordnet(
    clock: Clock,
): SakStatistikk {
    // TODO har vi en systembruker vi kan angi her? Og hvordanv et man om det er opprettet "manuelt"?
    val opprettetAv = "SU-app"
    // TODO må bekrefte at vedtak opprettes ved inverksettelse...

    return when (this) {
        is StatistikkEvent.Behandling.Søknad -> {
            when (this) {
                is StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.Iverksatt.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        ferdigbehandletTid = vedtak.opprettet,
                        behandlingResultat = BehandlingResultat.Avvist.name,
                        resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                        ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                            ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                    )
                }

                is StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        opprettetAv = opprettetAv,
                        ferdigbehandletTid = vedtak.opprettet,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.Iverksatt.name,
                        behandlingResultat = BehandlingResultat.Innvilget.name,
                        // TODO vanskelig å vite sikkert på dette tidspunktet...
                        utbetaltTid = null,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                            ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                    )
                }

                is StatistikkEvent.Behandling.Søknad.Lukket -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        opprettetAv = opprettetAv,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.Avsluttet.name,
                        // TODO hvordan vite om trukket eller avbrutt??
                        behandlingResultat = BehandlingResultat.Avbrutt.name,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                            ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                    )
                }

                is StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.Underkjent.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        behandlingResultat = null,
                        /*
                        TODO
                        resultatBegrunnelse = søknadsbehandling.attesteringer.hentSisteAttestering().let {
                            when (it) {
                                is Attestering.Underkjent -> when(it.grunn) {
                                    is UnderkjennAttesteringsgrunnBehandling -> it.grunn.name
                                    else -> throw IllegalStateException("Attestering til søknadsbehandling har feil type")
                                }
                                is Attestering.Iverksatt -> throw IllegalStateException("Underkjent søknadsbehandling har iverksatt attestering")
                            }
                        },
                         */
                        ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                            ?: throw IllegalStateException("Et inverksatt avslag kan ikke mangle attestant"),
                    )
                }

                is StatistikkEvent.Behandling.Søknad.Underkjent.Avslag -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.Underkjent.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        resultatBegrunnelse = utledAvslagsgrunner(this.søknadsbehandling.avslagsgrunner),
                    )
                }

                is StatistikkEvent.Behandling.Søknad.Opprettet -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.Registrert.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    )
                }

                is StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.TilAttestering.name,
                        behandlingResultat = BehandlingResultat.Avvist.name,
                        behandlingAarsak = utledAvslagsgrunner(søknadsbehandling.avslagsgrunner),
                        opprettetAv = opprettetAv,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    )
                }

                is StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.TilAttestering.name,
                        behandlingResultat = BehandlingResultat.Innvilget.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    )
                }
            }
        }

        is StatistikkEvent.Behandling.Klage,
        is StatistikkEvent.Behandling.Revurdering,
        is StatistikkEvent.Behandling.Stans,
        is StatistikkEvent.Behandling.Gjenoppta,
        is StatistikkEvent.Behandling.Omgjøring.AvslåttOmgjøring,
        -> {
            TODO()
            // this.toBehandlingsstatistikkGenerell()
        }
    }
}

private fun StatistikkEvent.Behandling.toBehandlingsstatistikkGenerell(
    clock: Clock,
    behandling: Stønadsbehandling,
    behandlingType: Behandlingstype,
    behandlingStatus: String,
    opprettetAv: String,
    relatertId: UUID? = null,
    behandlingAarsak: String? = null,
    saksbehandler: String? = null,
    ferdigbehandletTid: Tidspunkt? = null,
    utbetaltTid: LocalDate? = null,
    behandlingResultat: String? = null,
    resultatBegrunnelse: String? = null,
    ansvarligBeslutter: String? = null,

): SakStatistikk {
    return SakStatistikk(
        funksjonellTid = behandling.opprettet,
        tekniskTid = Tidspunkt.now(clock),
        sakId = behandling.sakId,
        saksnummer = behandling.saksnummer.nummer,
        behandlingId = behandling.id.value,
        // TODO er den nødvendig?
        relatertBehandlingId = relatertId,
        aktorId = behandling.fnr,
        sakYtelse = behandling.sakstype.toYtelseType().name,
        behandlingType = behandlingType.name,
        mottattTid = behandling.opprettet,
        registrertTid = behandling.opprettet,
        ferdigbehandletTid = ferdigbehandletTid,
        utbetaltTid = utbetaltTid,
        behandlingStatus = behandlingStatus,
        behandlingResultat = behandlingResultat,
        resultatBegrunnelse = resultatBegrunnelse,
        behandlingAarsak = behandlingAarsak,
        opprettetAv = opprettetAv,
        saksbehandler = saksbehandler,
        ansvarligBeslutter = ansvarligBeslutter,
    )
}
