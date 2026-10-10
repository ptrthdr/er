# Asynchroniczny Submit

Główny backend wysyła TrustedEvaluationRequest do
`POST /internal/v1/evaluation-jobs`. Odpowiedź HTTP 202 zawiera submissionId,
state, createdAt, startedAt, finishedAt, result i error. Następnie pobiera
`GET /internal/v1/evaluation-jobs/{submissionId}`. Oba endpointy wymagają
klucza serwerowego. Zalecany interwał odpytywania: 700–1000 ms.

Stany: QUEUED → RUNNING → FINISHED lub FAILED. FINISHED oznacza zakończenie
oceny; właściwy verdict, np. WRONG_ANSWER, znajduje się w result.status.
FAILED oznacza błąd obsługi zadania lub niepoprawne dane semantyczne.
Walidacja struktury, adaptera, języka, testów i limitów następuje przy POST,
przed przyjęciem zgłoszenia. Nie wysyłaj klucza ani zaufanych testów z przeglądarki.

Jeden wątek oceniający, 10 miejsc oczekujących, do 100 zachowanych zgłoszeń.
Przy pełnej kolejce lub magazynie: HTTP 429. Maksymalny serializowany payload
zgłoszenia: 2 MB (HTTP 413). Wyniki wygasają 30 minut po zakończeniu,
usuwanie następuje przy kolejnym GET/POST. Nieznany/wygasły identyfikator: 404.
Główny backend pobiera i zapisuje wynik trwale.

Identyczne ponowienie submissionId i całej treści zwraca istniejące zadanie
bez ponownego wykonania. Inna treść z tym samym ID: HTTP 409.
Deduplikacja jest lokalna, działa do wygaśnięcia wyniku lub restartu procesu.
Zadania i wyniki są w RAM: restart je usuwa. Nie jest to trwała kolejka ani
rozwiązanie dla wielu replik. Nie używaj równocześnie synchronicznych endpointów
oceny/Run do obciążenia sandboxa podczas benchmarków; omijają tę kolejkę.
Worker Judge0 nadal wymaga COUNT=1.

Demo używa POST /api/submission-jobs i GET /api/submission-jobs/{id}; są
włączone tylko w trybie demo. Główny backend zastępuje je własnymi endpointami
sprawdzającymi tożsamość i prawa do zgłoszenia. Endpointy demonstracyjne nie
zapewniają autoryzacji użytkowników.

Frontend przekazuje callbackowi onSubmit drugi argument `{onProgress}`.
Klient API może wywołać onProgress('QUEUED'|'RUNNING') podczas odpytywania,
a na końcu zwrócić samo result. Synchroniczne callbacki wciąż działają.
Zerwanie połączenia nie anuluje oceny. Identyfikator należy zachować w głównym
backendzie, aby później pobrać wynik zamiast wysyłać nowe zgłoszenie.

Dotychczasowe /internal/v1/evaluations i /api/submissions pozostają dostępne.
Nowe integracje powinny używać kolejki. Czas benchmarku nie jest skracany;
zmienia się obsługa oczekiwania i ograniczenie obciążenia.

## Wspólna kontrola sandboxa

Jedna instancja ExecutionService ma wspólną blokadę dla Run, Submit oraz
benchmarku, w obu silnikach. Blokada obejmuje całą ocenę i benchmark.
Endpointy synchroniczne czekają maksymalnie 2 sekundy na dostęp, następnie
zwracają 503 z informacją o zajętym sandboxie. Przyjęty Submit czeka na
blokadę bez takiego timeoutu i pozostaje QUEUED. RUNNING i startedAt ustawiane
są po uzyskaniu dostępu. Zatrzymanie modułu przerywa oczekiwanie; kolejka jest
nadal pamięciowa. Kolejka ogranicza liczbę przyjętych oczekujących zgłoszeń.
Blokada jest lokalna: wiele replik i bezpośrednie wywołania Judge0 wymagają
koordynacji zewnętrznej. Nie uruchamiaj drugiego backendu przy benchmarkowaniu.
