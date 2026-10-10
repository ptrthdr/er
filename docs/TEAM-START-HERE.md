# CodeLobby — start zespołu

Pakiet obejmuje lokalne MVP modułu wykonawczego. Pierwszym krokiem jest
uruchomienie i sprawdzenie na komputerze integratora. Wyniki dotychczasowych
sprawdzeń są w docs/verification; nie oznaczają zgodności z każdym hostem.

| Osoba | Co przeczytać / wykorzystać |
| --- | --- |
| Backend | backend-handoff.md, openapi.json, async-submissions.md, examples/team-backend |
| Frontend | frontend-handoff.md, frontend/src/execution-editor.js |
| Zadania/testy | task-authoring.md, languages.md, adaptery capabilities |
| Prezentacja / środowisko | team-quickstart.md, presentation-scenario.md, troubleshooting.md |

## Granice odpowiedzialności

Moduł: sterowniki języków, kompilacja, sandbox, wykonanie, testy, benchmark,
kolejka i kontrakt wyników; komponent edytora.
Główny backend: użytkownicy/uprawnienia, lobby i dozwolone języki, zadania,
testy, wersje, limity, trwały zapis wyników i rankingi. Frontend aplikacji:
konfiguracja edytora, stan zgłoszenia, komunikacja z głównym backendem.
Przeglądarka nigdy nie dostaje klucza /internal/v1 ani ukrytych expectedOutput.

## Źródła obowiązującego kontraktu

OpenAPI opisuje API; async-submissions opisuje kolejkę; function-benchmark
opisuje obecne metryki. Dokumenty submit-performance i wcześniejsze raporty
są historią pomiarów, nie obietnicą bieżących czasów. FUNCTION mierzy elapsed
czas wywołań, FULL_PROGRAM zewnętrzny CPU. W rankingu nie mieszaj zakresów.

## Status i ograniczenia

Lokalny Docker: przypięty nieoficjalny obraz amd64 i wymagany worker Ruby.
Jeden backend/worker; nie uruchamiaj równolegle innych instancji na tym samym
Judge0 przy benchmarkach. Kolejka RAM, max 10 oczekujących i 100 rekordów,
wyniki wygasają po 30 min; restart je usuwa. Przyjęte Submit czeka na sandbox.
Wewnętrzny czas funkcji nie jest odporny na celowe manipulacje. To MVP do
lokalnej integracji i prezentacji, nie zweryfikowane publiczne wdrożenie.

## Kryteria udanego przekazania

- Integrator uruchomił API i rzeczywiste wykonanie, nie tylko system_info.
- Własne zadanie backendu osiąga Accepted / Wrong Answer zgodnie z testami.
- Klucz pozostaje serwerowy, wyniki ukryte nie trafiają do przeglądarki.
- Status i wynik zgłoszenia są pobierane i zapisywane przez główny backend.
- Błędy 400/409/429/503 oraz utrata zgłoszenia po restarcie nie powodują
  nieskończonego oczekiwania ani automatycznych podwójnych ocen.
- Zespół uzgodnił wersję zadania/testów i zakres metryk rankingu.
