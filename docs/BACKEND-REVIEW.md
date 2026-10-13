# Przegląd runnera przez zespół backendu

To repo zawiera moduł wykonawczy CodeLobby, a nie główny backend aplikacji.
Celem tej wersji jest przegląd kontraktu i uzgodnienie integracji.

## Od czego zacząć

1. [Kontrakt i odpowiedzialności](backend-handoff.md).
2. [OpenAPI v1](openapi.json) - dokładne pola żądań i odpowiedzi.
3. [Przykłady żądań](../examples/integration/README.md).
4. [Uruchomienie lokalne](team-quickstart.md) - bez profilu demo.
5. [Testy](TESTING.md) i [ostatni raport](verification/current-check-2026-10-13.md).

## Co zawiera repo

- `runner/` - Spring Boot, wykonanie przez Judge0, ocena, kolejka i testy JUnit.
- `components/code-editor/` - komponent CodeMirror z callbackami dla frontendu.
- `infra/` - Compose, szablony konfiguracji i wymagany worker Judge0.
- `docs/` - kontrakt i instrukcje integracji.
- `examples/integration/` - przykładowe żądania, nie drugi backend.
- `scripts/` - walidacja kontraktu i sprawdzenia z rzeczywistym sandboxem.

Strona demonstracyjna i symulowany backend zespołu nie są częścią tej wersji.

## Co robi główny backend

Backend zarządza użytkownikami, lobby, zadaniami i uprawnieniami. Pobiera testy
oraz limity z zaufanego źródła. Łączy kod uczestnika ze starterem i wymaganymi
plikami, a następnie przekazuje komplet runnerowi. Klucz wewnętrznego API
pozostaje na serwerze. Uczestnik nie dostarcza expected output ani ukrytych testów.
Runner nie pobiera zadań z bazy i nie zna użytkowników ani lobby.

Zadania docelowo polegają na implementacji funkcji lub metody o ustalonej
sygnaturze. Aktualne adaptery są opisane w capabilities. Obsługa nowej sygnatury
wymaga adaptera wykonania, sama zmiana opisu zadania nie wystarczy.
Możliwość FULL_PROGRAM pozostaje w API jako techniczna funkcja runnera.

## Kwestie do wspólnego ustalenia

### Run a testy jawne

Docelowy przycisk Run ma sprawdzać wszystkie testy jawne i porównywać wyniki.
`POST /internal/v1/runs` obecnie uruchamia kod na jednym stdin. Accepted w tym
endpointcie oznacza poprawne zakończenie wykonania, nie zaliczenie zestawu testów.

Poprawka demonstratora korzysta z metody `ExecutionService.runPublicTests`:
backend wybiera wyłącznie jawne testy, runner kompiluje raz i ocenia cały zestaw
bez benchmarku. Metoda jest w kodzie runnera, ale nie ma jeszcze osobnego,
wersjonowanego endpointu wewnętrznego ani schematu Run-testów w OpenAPI.
Przed integracją docelowego Run trzeba wspólnie uzgodnić jego endpoint i kontrakt.
Nie należy zastępować tego oceną wyłącznie po exit code ani przesyłać expected
output z przeglądarki.

### Języki i zadania

Do uzgodnienia pozostaje jeden język lobby lub wybór z listy hosta.
Backend musi sprawdzać politykę języków niezależnie od wyboru w edytorze.
Host przygotowuje własne zadanie, starter, testy i limity przed utworzeniem lobby.
Zadanie musi mieć stabilną wersję i adapter zgodny z językiem i sygnaturą.

### Kolejka i przechowywanie

Kolejka runnera jest pamięciowa. Identyczne ponowienie submissionId odzyskuje
istniejący job tylko w ramach aktualnego procesu. Zmiana danych powoduje 409.
Po restarcie job może przestać istnieć. Decyzję o trwałym zapisie i odzyskiwaniu
podejmuje główny backend. Jeśli projekt użyje bazy aplikacji, planowany jest
PostgreSQL. Redis i PostgreSQL w Compose Judge0 to infrastruktura samego Judge0,
nie implementacja bazy głównego backendu ani trwałości kolejki runnera.

### Benchmark i ranking

Ocena poprawności poprzedza benchmark. Dla funkcji czas mierzy adapter,
a pamięć dotyczy całego procesu. Wyniki między językami wymagają uzgodnionych
zasad porównania. Ranking i opcjonalna ocena jakości kodu są poza runnerem.
Wewnętrzne pomiary funkcji mają oznaczenie IN_PROCESS_UNTRUSTED.

## Stan weryfikacji

Przed zmianą nazw katalogów ostatni pełny przebieg zakończył się wynikami
80/80 JUnit, 76/76 sprawdzeń Judge0 i 13/13 scenariuszy demonstracyjnego backendu.
Poprawka Run została dodatkowo sprawdzona dla wyników 1/3, 2/3 i 3/3.
Po zmianie nazw nie wykonano ponownie całego zestawu ani integracji z rzeczywistym
codelobby-backend. Workflow CI w tym repo ma wykonać testy niezależnie.

Lokalny wariant Judge0 dla Docker Desktop używa przypiętego obrazu społecznościowego.
Konfiguracja bazowa zachowuje oficjalny obraz. Środowisko i ograniczenia opisano
w infra i dokumentacji. Te sprawdzenia nie zastępują audytu bezpieczeństwa.
