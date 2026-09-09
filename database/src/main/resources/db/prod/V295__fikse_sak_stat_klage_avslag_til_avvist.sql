update sak_statistikk set behandling_resultat = 'AVVIST'
where behandling_id IN ('8cc57518-46f4-46d8-9199-f93e542c5ae6','459496da-1d0c-4ba0-a586-52dcf4a1bfb4','e14e482d-86f2-433c-999f-1eca86a09655')
  and behandling_resultat = 'AVSLAG';