# Kolejka Submit v1

Aktualny model i replay: [integration-contract.md](integration-contract.md).
POST /evaluation-jobs przyjmuje sourceFiles i taskVersionId. 202 dla nowych/
aktywnych jobów, 200 dla ponowień zakończonych. GET zwraca snapshot 200 lub 404.
QUEUED oznacza oczekiwanie również na wspólną blokadę sandboxa;
RUNNING/startedAt ustawiane po jej uzyskaniu. FINISHED jest oceną zakończoną,
nie automatycznie Accepted. FAILED ma obiekt error {code,message,retryable}.
GET zwraca HTTP 200 również dla FAILED. Awaria infrastruktury ma
EXECUTION_UNAVAILABLE/retryable=true, a nieoczekiwany błąd aplikacji
INTERNAL_SERVER_ERROR/retryable=false. Błędy walidacji zachowują własny kod.
Ponowienie POST z tym samym ID zwraca zapisany FAILED bez wykonania.
Nowa próba po błędzie przejściowym wymaga nowego ID, powiązanego z oryginalnym
zgłoszeniem po stronie backendu. Szczegóły: sekcja ponawiania w kontrakcie.

1 wątek, 10 oczekujących, 100 jobów, 30 minut retencji po zakończeniu.
Deduplikacja sortuje pliki po path; kolejność testów i zawartość pozostają istotne.
Restart usuwa stan i deduplikację. Backend utrwala zgłoszenia/wyniki i podejmuje
kontrolowane retry. Nie gwarantujemy exactly-once ani obsługi wielu replik.

Run czeka do 2 sekund na wspólny sandbox, następnie 503. Submit w kolejce
czeka przerywalnie do uzyskania dostępu. Blokada obejmuje poprawność i benchmark.
Bezpośrednie wywołania Judge0 lub inne instancje runnera omijają tę blokadę.

Demo /api/submission-jobs pozostaje opcjonalne poza domyślnym artefaktem.
Nie udostępniaj endpointów wewnętrznych ani klucza frontendowi.
