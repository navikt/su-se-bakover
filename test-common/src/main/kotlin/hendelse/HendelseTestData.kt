package no.nav.su.se.bakover.test.hendelse

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.SakOpprettetHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksbehandler
import java.util.UUID

fun sakOpprettetHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    opprettetAv: NavIdentBruker = saksbehandler,
    hendelsestidspunkt: Tidspunkt = fixedTidspunkt,
) = SakOpprettetHendelse.fraPersistert(
    hendelseId = hendelseId,
    sakId = sakId,
    fnr = fnr,
    opprettetAv = opprettetAv,
    hendelsestidspunkt = hendelsestidspunkt,
    entitetId = sakId,
    versjon = 1,
)

fun defaultHendelseMetadata(
    correlationId: CorrelationId? = correlationId(),
    ident: NavIdentBruker? = saksbehandler,
    brukerroller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant),
) = DefaultHendelseMetadata(
    correlationId = correlationId,
    ident = ident,
    brukerroller = brukerroller,
)

/**
 * Defaultverdiene er hentet fra en typisk melding fra oppdrag i preprod.
 */
fun jmsHendelseMetadata(
    jmsCorrelationId: String = "ID:f14040404040404040404040404040404040404040404040",
    jmsDeliveryMode: Int = 2,
    jmsDeliveryTime: Long = 0,
    jmsDestination: String? = null,
    jmsExpiration: Long = 0,
    jmsMessageId: String = "ID:c3e2d840d4d9d8f14040404040404040de331e9eada5b601",
    jmsPriority: Int = 0,
    jmsRedelivered: Boolean = false,
    jmsReplyTo: String? = null,
    jmsTimestamp: Long = 1699989414620,
    jmsType: String? = null,
    correlationId: CorrelationId = CorrelationId.generate(),
): JMSHendelseMetadata = JMSHendelseMetadata(
    jmsCorrelationId = jmsCorrelationId,
    jmsDeliveryMode = jmsDeliveryMode,
    jmsDeliveryTime = jmsDeliveryTime,
    jmsDestination = jmsDestination,
    jmsExpiration = jmsExpiration,
    jmsMessageId = jmsMessageId,
    jmsPriority = jmsPriority,
    jmsRedelivered = jmsRedelivered,
    jmsReplyTo = jmsReplyTo,
    jmsTimestamp = jmsTimestamp,
    jmsType = jmsType,
    correlationId = correlationId,
)

fun oppgaveHendelseMetadata(
    correlationId: CorrelationId = CorrelationId.generate(),
    ident: NavIdentBruker = saksbehandler,
    brukerroller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant),
    requestJson: String? = "{\"requestJson\": null}",
    responseJson: String = "{\"responseJson\": null}",
) = OppgaveHendelseMetadata(
    correlationId = correlationId,
    ident = ident,
    brukerroller = brukerroller,
    request = requestJson,
    response = responseJson,
)
