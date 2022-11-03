package no.nav.su.se.bakover.database.avstemming

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.persistence.antall
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlag
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class AvstemmingPostgresRepoTest {

    @Test
    fun `henter siste grensesnittsavstemming`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.databaseRepos.avstemming

            val zero = repo.hentSisteGrensesnittsavstemming(fagområde = Fagområde.SUUFORE)
            zero shouldBe null
            val utbetaling1 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third

            repo.opprettGrensesnittsavstemming(
                Avstemming.Grensesnittavstemming(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2020).startOfDay(),
                    tilOgMed = 2.januar(2020).startOfDay(),
                    utbetalinger = listOf(utbetaling1),
                    fagområde = Fagområde.SUUFORE,
                ),
            )
            val utbetaling2 =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third
            val second = Avstemming.Grensesnittavstemming(
                opprettet = fixedTidspunkt,
                fraOgMed = 3.januar(2020).startOfDay(),
                tilOgMed = 4.januar(2020).startOfDay(),
                utbetalinger = listOf(utbetaling2),
                avstemmingXmlRequest = "<Root></Root>",
                fagområde = Fagområde.SUUFORE,
            )

            repo.opprettGrensesnittsavstemming(second)

            repo.hentSisteGrensesnittsavstemming(fagområde = Fagområde.SUUFORE)!! shouldBe second
        }
    }

    @Test
    fun `hent utbetalinger for grensesnittsavstemming`() {
        withMigratedDb { dataSource ->
            val ellevteOktoberStart = 11.oktober(2021).startOfDay().fixedClock()
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val (_, vedtak, utbetaling) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakOgSøknad = sak to søknad,
                    clock = ellevteOktoberStart,
                )
            }.also { (_, _, utbetaling) ->
                utbetaling.avstemmingsnøkkel shouldBe Avstemmingsnøkkel(
                    11.oktober(2021).startOfDay(),
                )
            }

            dataSource.withSession { session ->
                """
                    insert into utbetaling (id, opprettet, sakId, fnr, behandler, avstemmingsnøkkel, simulering, utbetalingsrequest)
                    values (:id, :opprettet, :sakId, :fnr, :behandler, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json))
                """.insert(
                    mapOf(
                        "id" to UUID30.randomUUID(),
                        "opprettet" to fixedTidspunkt,
                        "sakId" to vedtak.behandling.sakId,
                        "fnr" to vedtak.behandling.fnr,
                        "behandler" to "Z123",
                        "avstemmingsnokkel" to objectMapper.writeValueAsString(
                            Avstemmingsnøkkel(
                                9.oktober(2021).startOfDay(),
                            ),
                        ),
                        "simulering" to "{}",
                        "utbetalingsrequest" to "{}",
                    ),
                    session,
                )
            }

            repo.hentUtbetalingerForGrensesnittsavstemming(
                fraOgMed = 10.oktober(2021).startOfDay(),
                tilOgMed = 10.oktober(2021).endOfDay(),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe emptyList()

            repo.hentUtbetalingerForGrensesnittsavstemming(
                fraOgMed = 11.oktober(2021).startOfDay(),
                tilOgMed = 11.oktober(2021).endOfDay(),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe listOf(utbetaling)

            repo.hentUtbetalingerForGrensesnittsavstemming(
                fraOgMed = 12.oktober(2021).startOfDay(),
                tilOgMed = 12.oktober(2021).endOfDay(),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe emptyList()
        }
    }

    @Test
    fun `hent utbetalinger for grensesnittsavstemming tidspunkt test`() {
        withMigratedDb { dataSource ->
            val ellevteOktoberStart = 11.oktober(2021).startOfDay().fixedClock()
            val ellevteOktoberSlutt = 11.oktober(2021).endOfDay().fixedClock()
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val (_, _, utbetaling1) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakOgSøknad = sak to søknad,
                    clock = ellevteOktoberStart,
                )
            }.also { (_, _, utbetaling) ->
                utbetaling.avstemmingsnøkkel shouldBe Avstemmingsnøkkel(
                    11.oktober(2021).startOfDay(),
                )
            }

            val (_, _, utbetaling2) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakOgSøknad = sak to søknad,
                    clock = ellevteOktoberSlutt,
                )
            }.also { (_, _, utbetaling) ->
                utbetaling.avstemmingsnøkkel shouldBe Avstemmingsnøkkel(
                    11.oktober(2021).endOfDay(),
                )
            }

            val utbetalinger = repo.hentUtbetalingerForGrensesnittsavstemming(
                fraOgMed = 11.oktober(2021).startOfDay(),
                tilOgMed = 11.oktober(2021).endOfDay(),
                fagområde = Fagområde.SUUFORE,
            )
            utbetalinger shouldHaveSize 2
            utbetalinger.map { it.id } shouldContainAll listOf(utbetaling1.id, utbetaling2.id)
        }
    }

    @Test
    fun `oppretter grensesnittsavstemming og oppdaterer aktuelle utbetalinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third

            val avstemming = Avstemming.Grensesnittavstemming(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                fraOgMed = fixedTidspunkt,
                tilOgMed = fixedTidspunkt,
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
                fagområde = Fagområde.SUUFORE,
            )

            repo.opprettGrensesnittsavstemming(avstemming)
            repo.oppdaterUtbetalingerEtterGrensesnittsavstemming(avstemming)

            dataSource.withSession { session ->
                "select count(*) from Utbetaling where avstemmingId is not null".antall(session = session)
            } shouldBe 1
        }
    }

    @Test
    fun `oppretter og henter konsistensavstemming`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third

            val avstemming = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = 1.januar(2020).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.januar(2021).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
                fagområde = Fagområde.SUUFORE,
            )

            repo.opprettKonsistensavstemming(avstemming)

            repo.hentKonsistensavstemming(avstemming.id) shouldBe Avstemming.Konsistensavstemming.Fullført(
                id = avstemming.id,
                opprettet = avstemming.opprettet,
                løpendeFraOgMed = avstemming.løpendeFraOgMed,
                opprettetTilOgMed = avstemming.opprettetTilOgMed,
                utbetalinger = mapOf(
                    oversendtUtbetalingMedKvittering.saksnummer to oversendtUtbetalingMedKvittering.utbetalingslinjer,
                ),
                avstemmingXmlRequest = "some xml",
                fagområde = Fagområde.SUUFORE,
            )
        }
    }

    @Test
    fun `finner ut om konsistensavstemming er utført for og på aktuell dato`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third

            val avstemming1 = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = 1.januar(2021).startOfDay(ZoneOffset.UTC),
                løpendeFraOgMed = 1.januar(2021).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.desember(2020).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
                fagområde = Fagområde.SUUFORE,
            )

            repo.opprettKonsistensavstemming(avstemming1)

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.januar(2021),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe true

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 31.desember(2020),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe false

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 2.januar(2021),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe false

            val avstemming2 = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = 1.februar(2021).startOfDay(ZoneOffset.UTC),
                løpendeFraOgMed = 1.april(2021).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.mars(2021).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
                fagområde = Fagområde.SUUFORE,
            )

            repo.opprettKonsistensavstemming(avstemming2)

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.februar(2021),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe false
            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.april(2021),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe false

            val avstemming3 = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = 1.mars(2021).startOfDay(ZoneOffset.UTC),
                løpendeFraOgMed = 1.juni(2021).startOfDay(zoneIdOslo),
                opprettetTilOgMed = 31.mai(2021).endOfDay(zoneIdOslo),
                utbetalinger = listOf(oversendtUtbetalingMedKvittering),
                avstemmingXmlRequest = "some xml",
                fagområde = Fagområde.SUUFORE,
            )

            repo.opprettKonsistensavstemming(avstemming3)

            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.mars(2021),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe false
            repo.konsistensavstemmingUtførtForOgPåDato(
                dato = 1.juni(2021),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe false
        }
    }

    @Test
    fun `konsistensavstemming henter kun utbetalinger hvor det eksisterer utbetalingslinjer med tom større enn eller lik løpendeFraOgMed`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay()
                    .plus(1, ChronoUnit.DAYS),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay()
                    .minus(1, ChronoUnit.DAYS),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.senesteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)
        }
    }

    @Test
    fun `konsistensavstemming henter bare utbetalinger opprettet tidligere enn opprettetTilOgMed`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val oversendtUtbetalingMedKvittering =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().third

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.plus(1, ChronoUnit.DAYS),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet.minus(1, ChronoUnit.DAYS),
                fagområde = Fagområde.SUUFORE,
            ) shouldBe emptyList()

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = oversendtUtbetalingMedKvittering.tidligsteDato().startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
                fagområde = Fagområde.SUUFORE,
            ) shouldBe listOf(oversendtUtbetalingMedKvittering)
        }
    }

    @Test
    fun `konsistensavstemming uten løpende utbetalinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo
            val avstemming = Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = 1.januar(2021).startOfDay(),
                opprettetTilOgMed = 1.januar(2021).endOfDay(),
                utbetalinger = emptyList(),
                avstemmingXmlRequest = "xml",
                fagområde = Fagområde.SUUFORE,
            )

            repo.opprettKonsistensavstemming(avstemming)

            repo.hentKonsistensavstemming(avstemming.id) shouldBe Avstemming.Konsistensavstemming.Fullført(
                id = avstemming.id,
                opprettet = avstemming.opprettet,
                løpendeFraOgMed = 1.januar(2021).startOfDay(),
                opprettetTilOgMed = 1.januar(2021).endOfDay(),
                utbetalinger = emptyMap(),
                avstemmingXmlRequest = "xml",
                fagområde = Fagområde.SUUFORE,
            )
        }
    }

    @Test
    fun `konsistensavstemming henter hele utbetalingen selv om bare en linje er løpende etter løpendeFraOgMed`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.avstemmingRepo

            val p1 = januar(2021)..april(2021)
            val p2 = mai(2021)..desember(2021)
            val (sak, _, oversendtUtbetalingMedKvittering) =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling { (sak, søknad) ->
                    iverksattSøknadsbehandlingUføre(
                        sakOgSøknad = sak to søknad,
                        customVilkår = listOf(
                            UføreVilkår.Vurdert.create(
                                vurderingsperioder = nonEmptyListOf(
                                    VurderingsperiodeUføre.create(
                                        id = UUID.randomUUID(),
                                        opprettet = fixedTidspunkt,
                                        vurdering = Vurdering.Innvilget,
                                        grunnlag = uføregrunnlag(opprettet = fixedTidspunkt, periode = p1, uføregrad = Uføregrad.parse(50)),
                                        periode = p1,
                                    ),
                                    VurderingsperiodeUføre.create(
                                        id = UUID.randomUUID(),
                                        opprettet = fixedTidspunkt,
                                        vurdering = Vurdering.Innvilget,
                                        grunnlag = uføregrunnlag(opprettet = fixedTidspunkt, periode = p2, uføregrad = Uføregrad.parse(40)),
                                        periode = p2,
                                    ),
                                ),
                            ),
                        ),
                        customGrunnlag = listOf(
                            fradragsgrunnlagArbeidsinntekt(periode = p1, arbeidsinntekt = 6500.0),
                            fradragsgrunnlagArbeidsinntekt(periode = p2, arbeidsinntekt = 6500.0),
                        ),
                    )
                }

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.januar(2021).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
                fagområde = Fagområde.SUUFORE,
            )[0].utbetalingslinjer shouldBe sak.utbetalinger.flatMap { it.utbetalingslinjer }.toNonEmptyList()

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.mai(2021).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
                fagområde = Fagområde.SUUFORE,
            )[0].utbetalingslinjer shouldBe sak.utbetalinger.flatMap { it.utbetalingslinjer }.toNonEmptyList()

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.desember(2021).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
                fagområde = Fagområde.SUUFORE,
            )[0].utbetalingslinjer shouldBe sak.utbetalinger.flatMap { it.utbetalingslinjer }.toNonEmptyList()

            repo.hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = 1.januar(2022).startOfDay(),
                opprettetTilOgMed = oversendtUtbetalingMedKvittering.opprettet,
                fagområde = Fagområde.SUUFORE,
            ) shouldBe emptyList()
        }
    }
}
