package no.nav.su.se.bakover.web.komponenttest

val antiktlint: Nothing = TODO("john skal se på dette en eller annen gang")
/*
class TilbakekrevingKomponentTest {
    @Test
    fun `happy path full tilbakekreving`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekreving(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().let { revurdering ->
                    revurdering.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>().also {
                        it.avgjort.sakId.toString() shouldBe sakid
                        it.avgjort.revurderingId.toString() shouldBe revurderingId
                    }
                    revurdering
                }

            appComponents.databaseRepos.utbetaling.hentOversendtUtbetalingForUtbetalingId(vedtak.utbetalingId, null)!!
                .shouldBeType<Utbetaling.OversendtUtbetaling.MedKvittering>()

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also {
                    it shouldBe emptyList()
                }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling

            appComponents.consumers.kravgrunnlagConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagKravgrunnlagPerioder(
                        mai(2021).until(oktober(2021)).map {
                            Feilutbetaling(
                                måned = it,
                                gammelUtbetaling = 21989,
                                nyUtbetaling = 3681,
                            )
                        },
                    )
                },
            )

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()

            appComponents.services.tilbakekrevingService.sendTilbakekrevingsvedtak {
                TilbakekrevingsmeldingMapper.toKravgrunnlag(it).getOrFail()
            }

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
                .also { tilbakekrevingsvedtak ->
                    tilbakekrevingsvedtak.tilbakekrevingsvedtakForsendelse.originalRequest() shouldContain """
                        <tilbakekrevingsbelop>
                            <kodeKlasse>SUUFORE</kodeKlasse>
                            <belopOpprUtbet>21989</belopOpprUtbet>
                            <belopNy>3681</belopNy>
                            <belopTilbakekreves>18308</belopTilbakekreves>
                            <belopUinnkrevd>0</belopUinnkrevd>
                            <belopSkatt>4729.00</belopSkatt>
                            <kodeResultat>FULL_TILBAKEKREV</kodeResultat>
                            <kodeAarsak>ANNET</kodeAarsak>
                            <kodeSkyld>BRUKER</kodeSkyld>
                        </tilbakekrevingsbelop>
                    """.replace("\n", "").trimWhitespace()
                }

            @Suppress("UNCHECKED_CAST")
            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter.single().also { brev ->
                        (
                            JSONObject(brev.generertDokumentJson).getJSONArray("tilbakekreving")
                                .map { it } as List<JSONObject>
                            )
                            .map { it.getString("beløp") }
                            .all { it == "13 579" } // 18308 - 4729 = 13579
                        brev.tittel shouldBe "Vi har vurdert den supplerende stønaden din på nytt og vil kreve tilbake penger"
                    }
                }
        }
    }

    @Test
    fun `happy path ingen tilbakekreving`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekreving(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().let { revurdering ->
                    revurdering.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>().also {
                        it.avgjort.sakId.toString() shouldBe sakid
                        it.avgjort.revurderingId.toString() shouldBe revurderingId
                    }
                    revurdering
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also {
                    it shouldBe emptyList()
                }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling

            appComponents.consumers.kravgrunnlagConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagKravgrunnlagPerioder(
                        mai(2021).until(oktober(2021)).map {
                            Feilutbetaling(
                                måned = it,
                                gammelUtbetaling = 21989,
                                nyUtbetaling = 3681,
                            )
                        },
                    )
                },
            )

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()

            appComponents.sendTilbakekrevingsvedtakTilØkonomi()

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
                .also { tilbakekrevingsvedtak ->
                    tilbakekrevingsvedtak.tilbakekrevingsvedtakForsendelse.originalRequest() shouldContain """
                        <tilbakekrevingsbelop>
                            <kodeKlasse>SUUFORE</kodeKlasse>
                            <belopOpprUtbet>21989</belopOpprUtbet>
                            <belopNy>3681</belopNy>
                            <belopTilbakekreves>0</belopTilbakekreves>
                            <belopUinnkrevd>18308</belopUinnkrevd>
                            <belopSkatt>0</belopSkatt>
                            <kodeResultat>INGEN_TILBAKEKREV</kodeResultat>
                            <kodeAarsak>ANNET</kodeAarsak>
                            <kodeSkyld>IKKE_FORDELT</kodeSkyld>
                        </tilbakekrevingsbelop>
                    """.replace("\n", "").trimWhitespace()
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter.single().also { brev ->
                        brev.tittel shouldBe "Vi har vurdert den supplerende stønaden din på nytt"
                    }
                }
        }
    }

    @Test
    fun `kaster hvis vi ikke finner tilbakekrevingsbehandling for kravgrunnlag`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekreving(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakInnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.kravgrunnlagConsumer.onMessage(
                    // tuller litt med utbetalingsid
                    lagKravgrunnlag(vedtak.copy(utbetalingId = UUID30.randomUUID())) {
                        lagKravgrunnlagPerioder(
                            mai(2021).until(oktober(2021)).map {
                                Feilutbetaling(
                                    måned = it,
                                    gammelUtbetaling = 21989,
                                    nyUtbetaling = 3681,
                                )
                            },
                        )
                    },
                )
            }.also {
                it.message shouldContain "Forventet å finne 1 tilbakekrevingsbehandling som avventer kravgrunnlag for utbetalingId:"
            }
        }
    }

    @Test
    fun `kaster hvis det ikke er samsvar mellom beløpene i simuleringen og kravgrunnlaget`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekreving(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakInnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.kravgrunnlagConsumer.onMessage(
                    lagKravgrunnlag(vedtak) {
                        lagKravgrunnlagPerioder(
                            mai(2021).until(oktober(2021)).map {
                                Feilutbetaling(
                                    måned = it,
                                    gammelUtbetaling = 17500,
                                    nyUtbetaling = 15,
                                )
                            },
                        )
                    },
                )
            }.also {
                it.message shouldContain "Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:"
            }
        }
    }

    @Test
    fun `kaster hvis det ikke er samsvar mellom periodene i simuleringen og kravgrunnlaget`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekreving(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakInnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.kravgrunnlagConsumer.onMessage(
                    lagKravgrunnlag(vedtak) {
                        lagKravgrunnlagPerioder(
                            mai(2021).until(juni(2021)).map {
                                Feilutbetaling(
                                    måned = it,
                                    gammelUtbetaling = 21989,
                                    nyUtbetaling = 3681,
                                )
                            },
                        )
                    },
                )
            }.also {
                it.message shouldContain "Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:"
            }
        }
    }

    @Test
    fun `send tilbakekrevingsvedtak lagrer ingenting dersom kall til økonomi feiler`() {
        val clock = TikkendeKlokke(1.oktober(2021).fixedClock())
        withKomptestApplication(
            clock = clock,
            clientsBuilder = { databaseRepos, klokke ->
                TestClientsBuilder(
                    clock = klokke,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).copy(
                    tilbakekrevingClient = mock {
                        on { sendTilbakekrevingsvedtak(any()) } doReturn TilbakekrevingsvedtakForsendelseFeil.left()
                    },
                )
            },
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekreving(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }

            appComponents.consumers.kravgrunnlagConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagKravgrunnlagPerioder(
                        mai(2021).until(oktober(2021)).map {
                            Feilutbetaling(
                                måned = it,
                                gammelUtbetaling = 21989,
                                nyUtbetaling = 3681,
                            )
                        },
                    )
                },
            )

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()
                }

            assertThrows<RuntimeException> {
                appComponents.sendTilbakekrevingsvedtakTilØkonomi()
            }.also {
                it.message shouldContain "Feil ved oversendelse av tilbakekrevingsvedtak for tilbakekrevingsbehandling"
            }

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id)) shouldBe emptyList()
        }
    }

    @Test
    fun `kan velge å ikke sende ut brev for tilbakekrevingsvedtak`() {
        val clock = TikkendeKlokke(1.oktober(2021).fixedClock())
        withKomptestApplication(
            clock = clock,
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekreving(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                brevvalg = { sakId, revurderingId ->
                    velgIkkeSendBrev(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        client = this.client,
                    )
                },
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }

            appComponents.consumers.kravgrunnlagConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagKravgrunnlagPerioder(
                        mai(2021).until(oktober(2021)).map {
                            Feilutbetaling(
                                måned = it,
                                gammelUtbetaling = 21989,
                                nyUtbetaling = 3681,
                            )
                        },
                    )
                },
            )

            appComponents.sendTilbakekrevingsvedtakTilØkonomi()

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id)) shouldBe emptyList()
            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForRevurdering(vedtak.behandling.id)) shouldBe emptyList()
            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
        }
    }

    private fun vedtakMedTilbakekreving(
        avgjørelse: TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson,
        client: HttpClient,
        brevvalg: (sakId: String, revurderingId: String) -> String = { sakId, revurderingId ->
            velgSendBrev(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
            )
        },
        appComponents: AppComponents,
    ): Pair<String, String> {
        return opprettInnvilgetSøknadsbehandling(
            fnr = Fnr.generer().toString(),
            fraOgMed = 1.januar(2021).toString(),
            tilOgMed = 31.desember(2021).toString(),
            client = client,
            appComponents = appComponents,
        ).let { søknadsbehandlingJson ->
            val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

            val revurderingId = opprettRevurdering(
                sakId = sakId,
                fraOgMed = 1.mai(2021).toString(),
                tilOgMed = 31.desember(2021).toString(),
                client = client,
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            leggTilFradrag(
                sakId = sakId,
                behandlingId = revurderingId,
                fraOgMed = 1.mai(2021).toString(),
                tilOgMed = 31.desember(2021).toString(),
                body = {
                    """
                        {
                          "fradrag": [
                            {
                              "periode": {
                                "fraOgMed": "${1.mai(2021)}",
                                "tilOgMed": "${31.desember(2021)}"
                              },
                              "type": "Arbeidsinntekt",
                              "beløp": 18308.0,
                              "utenlandskInntekt": null,
                              "tilhører": "BRUKER"
                            }
                          ]
                        }
                        """
                },
                client = client,
            )
            beregnOgSimuler(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
            )
            brevvalg(
                sakId,
                revurderingId,
            )
            avgjørTilbakekreving(
                sakId = sakId,
                behandlingId = revurderingId,
                avgjørelse = { """{"avgjørelse":"$avgjørelse"}""" },
                client = client,
            )
            sendTilAttestering(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
            )
            iverksett(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
                appComponents = appComponents,
            )

            sakId to revurderingId
        }
    }
}

 */
