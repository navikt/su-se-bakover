package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgavePrioritet
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Data
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import java.util.UUID

internal fun interface FradragssjekkOppgaveoppretter {
    fun opprett(config: OppgaveConfig.Fradragssjekk, nokkelord: Set<NøkkelOrd>): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}
enum class NøkkelOrd {
    FRADRAGSSJEKK,
}

internal class MiljøstyrtFradragssjekkOppgaveoppretter(
    private val oppgaveService: OppgaveService,
    private val oppgaveV2Client: OppgaveV2Client,
    private val brukOppgaveV2: Boolean,
) : FradragssjekkOppgaveoppretter {
    override fun opprett(config: OppgaveConfig.Fradragssjekk, nokkelord: Set<NøkkelOrd>): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
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

internal fun OppgaveConfig.Fradragssjekk.toOppgaveV2Config(nokkelord: Set<NøkkelOrd> = emptySet()): OppgaveV2Data {
    return OppgaveV2Data(
        nokkelord = nokkelord.map { it.name }.toSet(),
        beskrivelse = beskrivelse,
        kategorisering = OppgaveV2Data.Kategorisering(
            tema = OppgaveV2Data.Kode(Tema.SUPPLERENDE_STØNAD.value),
            oppgavetype = OppgaveV2Data.Kode(oppgavetype.toString()),
            behandlingstema = behandlingstema.let { OppgaveV2Data.Kode(it.toString()) },
            behandlingstype = OppgaveV2Data.Kode(behandlingstype.toString()),
        ),
        bruker = OppgaveV2Data.Bruker(
            ident = fnr.toString(),
            type = OppgaveV2Data.Bruker.Type.PERSON,
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

private fun OppgavePrioritet.toOppgaveV2Prioritet(): OppgaveV2Data.Prioritet {
    return when (this) {
        OppgavePrioritet.NORM -> OppgaveV2Data.Prioritet.NORMAL
        OppgavePrioritet.HOY -> OppgaveV2Data.Prioritet.HOY
        OppgavePrioritet.LAV -> OppgaveV2Data.Prioritet.LAV
    }
}
