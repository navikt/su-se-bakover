package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgavePrioritet
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Config
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import java.util.UUID
import kotlin.collections.List

internal fun interface FradragssjekkOppgaveoppretter {
    fun opprett(config: OppgaveConfig.Fradragssjekk, nokkelord: List<NøkkelOrd>): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}
enum class NøkkelOrd {
    FRADRAGSSJEKK,
}

internal class MiljøstyrtFradragssjekkOppgaveoppretter(
    private val oppgaveService: OppgaveService,
    private val oppgaveV2Client: OppgaveV2Client,
    private val brukOppgaveV2: Boolean,
) : FradragssjekkOppgaveoppretter {
    override fun opprett(config: OppgaveConfig.Fradragssjekk, nokkelord: List<NøkkelOrd>): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return if (brukOppgaveV2) {
            oppgaveV2Client.opprettOppgaveMedSystembruker(
                config = config.toOppgaveV2Config(nokkelord),
                idempotencyKey = config.toOppgaveV2IdempotencyKey(),
            )
        } else {
            oppgaveService.opprettOppgaveMedSystembruker(config)
        }
    }
}

internal fun OppgaveConfig.Fradragssjekk.toOppgaveV2Config(nokkelord: List<NøkkelOrd> = emptyList()): OppgaveV2Config {
    return OppgaveV2Config(
        nokkelord = nokkelord.map { it.name }.toSet(),
        beskrivelse = beskrivelse,
        kategorisering = OppgaveV2Config.Kategorisering(
            tema = OppgaveV2Config.Kode(Tema.SUPPLERENDE_STØNAD.value),
            oppgavetype = OppgaveV2Config.Kode(oppgavetype.toString()),
            behandlingstema = behandlingstema.let { OppgaveV2Config.Kode(it.toString()) },
            behandlingstype = OppgaveV2Config.Kode(behandlingstype.toString()),
        ),
        bruker = OppgaveV2Config.Bruker(
            ident = fnr.toString(),
            type = OppgaveV2Config.Bruker.Type.PERSON,
        ),
        aktivDato = aktivDato,
        fristDato = fristFerdigstillelse,
        prioritet = prioritet.toOppgaveV2Prioritet(),
        tilknyttetSystem = behandlesAvApplikasjon,
    )
}

internal fun OppgaveConfig.Fradragssjekk.toOppgaveV2IdempotencyKey(): UUID {
    return UUID.nameUUIDFromBytes("fradragssjekk-oppgave-v2|$saksreferanse|$måned".toByteArray())
}

private fun OppgavePrioritet.toOppgaveV2Prioritet(): OppgaveV2Config.Prioritet {
    return when (this) {
        OppgavePrioritet.NORM -> OppgaveV2Config.Prioritet.NORMAL
        OppgavePrioritet.HOY -> OppgaveV2Config.Prioritet.HOY
        OppgavePrioritet.LAV -> OppgaveV2Config.Prioritet.LAV
    }
}
