package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Config
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse

internal fun interface FradragssjekkOppgaveoppretter {
    fun opprett(config: OppgaveConfig.Fradragssjekk): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}

internal class MiljøstyrtFradragssjekkOppgaveoppretter(
    private val oppgaveService: OppgaveService,
    private val oppgaveV2Client: OppgaveV2Client,
    private val brukOppgaveV2: Boolean,
) : FradragssjekkOppgaveoppretter {
    override fun opprett(config: OppgaveConfig.Fradragssjekk): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return if (brukOppgaveV2) {
            oppgaveV2Client.opprettOppgaveMedSystembruker(config.toOppgaveV2Config())
        } else {
            oppgaveService.opprettOppgaveMedSystembruker(config)
        }
    }
}

internal fun OppgaveConfig.Fradragssjekk.toOppgaveV2Config(): OppgaveV2Config {
    return OppgaveV2Config(
        beskrivelse = buildString {
            append("Vurder fradragssjekk for sak ")
            append(saksnummer)
            append(" for måned ")
            append(måned)
            append('.')
            avvik.forEach {
                append("\n- ")
                append(it.tekst)
            }
        },
        kategorisering = OppgaveV2Config.Kategorisering(
            tema = OppgaveV2Config.Kode(Tema.SUPPLERENDE_STØNAD.value),
            oppgavetype = OppgaveV2Config.Kode(oppgavetype.toString()),
            behandlingstema = behandlingstema?.let { OppgaveV2Config.Kode(it.toString()) },
            behandlingstype = OppgaveV2Config.Kode(behandlingstype.toString()),
        ),
        bruker = OppgaveV2Config.Bruker(
            ident = fnr.toString(),
            type = OppgaveV2Config.Bruker.Type.PERSON,
        ),
        aktivDato = aktivDato,
        fristDato = fristFerdigstillelse,
        tilknyttetApplikasjon = behandlesAvApplikasjon,
    )
}
