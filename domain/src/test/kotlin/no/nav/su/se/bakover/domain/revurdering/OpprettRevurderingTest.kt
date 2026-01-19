package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import behandling.klage.domain.KlageId
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.finnRelatertIdOmgjøringKlage
import no.nav.su.se.bakover.domain.revurdering.opprett.kanOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.behandling.nyeKlager
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OpprettRevurderingTest {

    @Test
    fun `kan opprette revurdering dersom det ikke finnes eksisterende åpne behandlinger`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre(stønadsperiode = stønadsperiode2021)).first

        sakUtenÅpenBehandling.kanOppretteRevurdering(
            cmd = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakUtenÅpenBehandling.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `Omgjøringårsak krever omgjøringsgrunn`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre(stønadsperiode = stønadsperiode2021)).first
        val klage = OpprettetKlage(
            id = KlageId.generer(),
            opprettet = fixedTidspunkt,
            sakId = sakUtenÅpenBehandling.id,
            saksnummer = sakUtenÅpenBehandling.saksnummer,
            fnr = sakUtenÅpenBehandling.fnr,
            journalpostId = JournalpostId(value = "1"),
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            datoKlageMottatt = 1.januar(2021),
            sakstype = sakUtenÅpenBehandling.type,
        )
        val sakMedKlage = sakUtenÅpenBehandling.nyeKlager(listOf(klage))

        sakMedKlage.kanOppretteRevurdering(
            cmd = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN.name,
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakMedKlage.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeLeft().let {
            it shouldBe KunneIkkeOppretteRevurdering.MåhaOmgjøringsgrunn
        }
    }

    @Test
    fun `kan opprette revurdering dersom det finnes en åpen revurdering`() {
        val sakMedÅpenRevurdering = opprettetRevurdering().first

        sakMedÅpenRevurdering.kanOppretteRevurdering(
            cmd = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakMedÅpenRevurdering.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `kan opprette revurdering dersom det finnes en åpen regulering`() {
        val sakMedÅpenRegulering = innvilgetSøknadsbehandlingMedÅpenRegulering(mai(2021)).first
        sakMedÅpenRegulering.kanOppretteRevurdering(
            cmd = OpprettRevurderingCommand(
                saksbehandler = saksbehandler,
                årsak = "MELDING_FRA_BRUKER",
                informasjonSomRevurderes = nonEmptyListOf(Revurderingsteg.Bosituasjon),
                periode = stønadsperiode2021.periode,
                sakId = sakMedÅpenRegulering.id,
                begrunnelse = "begrunnelsen",
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `oppdatert tidspunkt er lik opprettet ved opprettelse - oppdatert skal være ulik opprettet ved oppdatering`() {
        val opprettetRevurdering = opprettetRevurdering().second
        opprettetRevurdering.opprettet shouldBe opprettetRevurdering.oppdatert

        val oppdatertRevurdering = opprettetRevurdering.oppdater(
            clock = enUkeEtterFixedClock,
            periode = opprettetRevurdering.periode,
            revurderingsårsak = Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.DØDSFALL,
                begrunnelse = Revurderingsårsak.Begrunnelse.create("Oppdaterer med ny årsak. Oppdatert tidspunktet skal være endret, og ikke lik opprettet"),
            ),
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                grunnlagsdata = opprettetRevurdering.grunnlagsdata,
                vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
            ),
            informasjonSomRevurderes = opprettetRevurdering.informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = opprettetRevurdering.vedtakSomRevurderesMånedsvis,
            tilRevurdering = opprettetRevurdering.tilRevurdering,
            saksbehandler = opprettetRevurdering.saksbehandler,
            omgjøringsgrunn = null,
        ).getOrFail()

        oppdatertRevurdering.oppdatert shouldNotBe oppdatertRevurdering.opprettet
        oppdatertRevurdering.oppdatert shouldBe Tidspunkt.now(enUkeEtterFixedClock)
        oppdatertRevurdering.opprettet shouldBe opprettetRevurdering.opprettet
    }

    internal class FinneRelatertIdTest {
        internal class OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN {
            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN - oversendt klage med opprettet KA hendelse gir IKKE klageId`() {
                val klageId = KlageId.generer()
                val klage = oversendtKlage(klageId = klageId).second
                // Feks OmgjoeringskravbehandlingAvsluttet
                val tolketKlageinstanshendelse = TolketKlageinstanshendelse.AnkebehandlingOpprettet(
                    UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    klageId = klage.id,
                    mottattKlageinstans = fixedTidspunkt,
                )
                val ans = klage.leggTilNyKlageinstanshendelse(
                    tolketKlageinstanshendelse,
                    { Either.Right(OppgaveId("UUID.randomUUID()")) },
                )

                val nyKlageMedInstansehendelse =
                    ans.getOrElse { throw IllegalStateException("Klage med ny instans hendelse skal kunne opprettes") }
                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )

                finnRelatertIdOmgjøringKlage(
                    klage = nyKlageMedInstansehendelse,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeLeft().let { it shouldBe KunneIkkeOppretteRevurdering.IngenAvsluttedeKlageHendelserFraKA }
            }

            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN - oversendt klage med avsluttet KA hendelse gir klageId`() {
                val klageId = KlageId.generer()
                val klage = oversendtKlage(klageId = klageId).second
                // Feks OmgjoeringskravbehandlingAvsluttet, må være en avsluttet
                val tolketKlageinstanshendelse = TolketKlageinstanshendelse.OmgjoeringskravbehandlingAvsluttet(
                    UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    avsluttetTidspunkt = fixedTidspunkt,
                    klageId = klage.id,
                    utfall = AvsluttetKlageinstansUtfall.KreverHandling.DelvisMedhold,
                    emptyList(),
                )
                val ans = klage.leggTilNyKlageinstanshendelse(
                    tolketKlageinstanshendelse,
                    { Either.Right(OppgaveId("UUID.randomUUID()")) },
                )

                val nyKlageMedInstansehendelse =
                    ans.getOrElse { throw IllegalStateException("Klage med ny instans hendelse skal kunne opprettes") }
                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )

                finnRelatertIdOmgjøringKlage(
                    klage = nyKlageMedInstansehendelse,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeRight(klage.id)
            }

            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN - oversendt klage uten hendelse går ikke`() {
                val klageId = KlageId.generer()
                val klage = oversendtKlage(klageId = klageId).second

                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )

                finnRelatertIdOmgjøringKlage(
                    klage = klage,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeLeft().let { it shouldBe KunneIkkeOppretteRevurdering.IngenKlageHendelserFraKA }
            }

            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN må være oversendt`() {
                val klage = påbegyntVurdertKlage().second

                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )
                finnRelatertIdOmgjøringKlage(
                    klage = klage,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeLeft().let { it shouldBe KunneIkkeOppretteRevurdering.KlageErIkkeOversendt }
            }
        }

        internal class OMGJØRING_KLAGE {
            @Test
            fun `OMGJØRING_KLAGE - oversendt klage med AnkeITrygderettenAvsluttet KA hendelse gir klageId`() {
            }
        }

        internal class OMGJØRING_TRYGDERETTEN {
            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN - oversendt klage med AnkeITrygderettenAvsluttet KA hendelse gir klageId`() {
                val klageId = KlageId.generer()
                val klage = oversendtKlage(klageId = klageId).second
                // Må være AnkeITrygderettenAvsluttet
                val tolketKlageinstanshendelse = TolketKlageinstanshendelse.AnkeITrygderettenAvsluttet(
                    UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    klageId = klage.id,
                    avsluttetTidspunkt = fixedTidspunkt,
                    utfall = AvsluttetKlageinstansUtfall.KreverHandling.DelvisMedhold,
                    journalpostIDer = emptyList(),
                )
                val ans = klage.leggTilNyKlageinstanshendelse(
                    tolketKlageinstanshendelse,
                    { Either.Right(OppgaveId("UUID.randomUUID()")) },
                )

                val nyKlageMedInstansehendelse =
                    ans.getOrElse { throw IllegalStateException("Klage med ny instans hendelse skal kunne opprettes") }
                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_TRYGDERETTEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )

                finnRelatertIdOmgjøringKlage(
                    klage = nyKlageMedInstansehendelse,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeRight(nyKlageMedInstansehendelse.id)
            }

            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN - oversendt klage med opprettet KA hendelse gir IKKE klageId`() {
                val klageId = KlageId.generer()
                val klage = oversendtKlage(klageId = klageId).second
                // Må være AnkeITrygderettenAvsluttet
                val tolketKlageinstanshendelse = TolketKlageinstanshendelse.AnkebehandlingOpprettet(
                    UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    klageId = klage.id,
                    mottattKlageinstans = fixedTidspunkt,
                )
                val ans = klage.leggTilNyKlageinstanshendelse(
                    tolketKlageinstanshendelse,
                    { Either.Right(OppgaveId("UUID.randomUUID()")) },
                )

                val nyKlageMedInstansehendelse =
                    ans.getOrElse { throw IllegalStateException("Klage med ny instans hendelse skal kunne opprettes") }
                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_TRYGDERETTEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )

                finnRelatertIdOmgjøringKlage(
                    klage = nyKlageMedInstansehendelse,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeLeft().let { it shouldBe KunneIkkeOppretteRevurdering.IngenTrygderettenAvsluttetHendelser }
            }

            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN - oversendt klage må ha hendelser`() {
                val klageId = KlageId.generer()
                val klage = oversendtKlage(klageId = klageId).second

                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_TRYGDERETTEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )

                finnRelatertIdOmgjøringKlage(
                    klage = klage,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeLeft().let { it shouldBe KunneIkkeOppretteRevurdering.IngenKlageHendelserFraKA }
            }

            @Test
            fun `OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN - oversendt klage må være oversendt `() {
                val klage = påbegyntVurdertKlage().second

                val årsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.OMGJØRING_TRYGDERETTEN,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("delvis medhold feks"),
                )

                finnRelatertIdOmgjøringKlage(
                    klage = klage,
                    revurderingsårsak = årsak,
                    sakId = UUID.randomUUID(),
                    omgjøringsGrunn = null,
                ).shouldBeLeft().let { it shouldBe KunneIkkeOppretteRevurdering.KlageErIkkeOversendt }
            }
        }
    }
}
