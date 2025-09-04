package no.nav.su.se.bakover.statistikk.sak

import arrow.core.Either
import behandling.domain.Stønadsbehandling
import behandling.revurdering.domain.Opphørsgrunn
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.SchemaValidator
import no.nav.su.se.bakover.statistikk.ValidertStatistikkJsonMelding
import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
import no.nav.su.se.bakover.statistikk.behandling.Behandlingstype
import statistikk.domain.SakStatistikk
import vilkår.common.domain.Avslagsgrunn
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

private val sakSchema: JsonSchema = SchemaValidator.createSchema("/statistikk/sak_schema.json")

internal fun StatistikkEvent.SakOpprettet.toBehandlingsstatistikk(
    aktørId: AktørId,
    gitCommit: GitCommit?,
): Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding> {
    return SaksstatistikkDto(
        funksjonellTid = sak.opprettet,
        tekniskTid = sak.opprettet,
        opprettetDato = sak.opprettet.toLocalDate(zoneIdOslo),
        sakId = sak.id,
        aktorId = aktørId.toString().toLong(),
        saksnummer = sak.saksnummer.nummer,
        sakStatus = "OPPRETTET",
        sakStatusBeskrivelse = "Sak er opprettet men ingen vedtak er fattet.",
        versjon = gitCommit?.value,
        ytelseType = sak.type.toYtelseType(),
    ).let {
        serialize(it).let {
            SchemaValidator.validate(it, sakSchema).map {
                ValidertStatistikkJsonMelding(
                    topic = "supstonad.aapen-su-sak-statistikk-v1",
                    validertJsonMelding = it,
                )
            }
        }
    }
}

internal fun StatistikkEvent.Behandling.toBehandlingsstatistikkOverordnet(
    clock: Clock,
): SakStatistikk {
    // TODO har vi en systembruker vi kan angi her? Og hvordanv et man om det er opprettet "manuelt"?
    val opprettetAv = "SU-app"

    return when (this) {
        is StatistikkEvent.Behandling.Søknad -> {
            when (this) {
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

                is StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = søknadsbehandling,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.Underkjent.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        behandlingResultat = BehandlingResultat.Innvilget.name,
                        ansvarligBeslutter = søknadsbehandling.hentAttestantSomIverksatte()?.navIdent
                            ?: throw IllegalStateException("Et underkjent avslag kan ikke mangle attestant"),
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
            }
        }

        is StatistikkEvent.Behandling.Revurdering -> {
            when (this) {
                is StatistikkEvent.Behandling.Revurdering.Opprettet -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.REVURDERING,
                        behandlingStatus = BehandlingStatus.Registrert.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    )
                }

                is StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.SOKNAD,
                        behandlingStatus = BehandlingStatus.TilAttestering.name,
                        behandlingResultat = BehandlingResultat.Innvilget.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                    )
                }

                is StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.REVURDERING,
                        behandlingStatus = BehandlingStatus.TilAttestering.name,
                        behandlingResultat = BehandlingResultat.OpphørtRevurdering.name,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                        resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                    )
                }

                is StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.REVURDERING,
                        behandlingStatus = BehandlingStatus.Underkjent.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                        behandlingResultat = BehandlingResultat.Innvilget.name,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                        ansvarligBeslutter = revurdering.hentAttestantSomIverksatte()?.navIdent
                            ?: throw IllegalStateException("Et underkjent avslag kan ikke mangle attestant"),
                    )
                }
                is StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.REVURDERING,
                        behandlingStatus = BehandlingStatus.Underkjent.name,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                        behandlingResultat = BehandlingResultat.OpphørtRevurdering.name,
                        resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                    )
                }
                is StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.REVURDERING,
                        behandlingStatus = BehandlingStatus.Iverksatt.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                        behandlingResultat = BehandlingResultat.Innvilget.name,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                        ansvarligBeslutter = revurdering.hentAttestantSomIverksatte()?.navIdent
                            ?: throw IllegalStateException("Et underkjent avslag kan ikke mangle attestant"),
                    )
                }
                is StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.REVURDERING,
                        behandlingStatus = BehandlingStatus.Iverksatt.name,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                        behandlingResultat = BehandlingResultat.OpphørtRevurdering.name,
                        resultatBegrunnelse = listUtOpphørsgrunner(this.revurdering.utledOpphørsgrunner(clock)),
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                    )
                }
                is StatistikkEvent.Behandling.Revurdering.Avsluttet -> {
                    this.toBehandlingsstatistikkGenerell(
                        clock = clock,
                        behandling = revurdering,
                        behandlingType = Behandlingstype.REVURDERING,
                        behandlingStatus = BehandlingStatus.Avsluttet.name,
                        behandlingAarsak = revurdering.revurderingsårsak.årsak.name,
                        opprettetAv = opprettetAv,
                        saksbehandler = revurdering.saksbehandler.navIdent,
                    )
                }
            }
        }

        is StatistikkEvent.Behandling.Klage,
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

private fun utledAvslagsgrunner(avslagsgrunner: List<Avslagsgrunn>): String? {
    return if (avslagsgrunner.isEmpty()) null else avslagsgrunner.joinToString(",")
}

private fun listUtOpphørsgrunner(opphørsgrunner: List<Opphørsgrunn>): String? {
    return if (opphørsgrunner.isEmpty()) null else opphørsgrunner.joinToString(",")
}
