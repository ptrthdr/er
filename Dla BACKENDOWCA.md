# Dla BACKENDOWCA — integracja z modułem CodeLobby

Ten dokument opisuje aktualną wersję modułu wykonawczego: co dostajesz,
co musisz przygotować w głównym backendzie i jak połączyć obie części.
Dokładny kontrakt pól znajduje się w [OpenAPI](docs/openapi.json).

## 1. Co robi ten moduł

Przyjmuje kod oraz zaufane dane zadania. Kompiluje i wykonuje kod w Judge0,
sprawdza testy, porównuje wyniki i benchmarkuje poprawne rozwiązania.
Obsługuje C++, Python 3 oraz Javę. Dostarcza też komponent edytora CodeMirror 6.

Przepływ docelowy:

```text
Frontend studenta → główny backend CodeLobby → moduł wykonawczy → Judge0
                                            ← wynik oceny ←
```

Główny backend korzysta z API Spring Boot modułu. Nie musi wywoływać Judge0
bezpośrednio, tworzyć maina ani znać Ruby używanego przez worker Judge0.

## 2. Podział odpowiedzialności

| Główny backend CodeLobby | Moduł wykonawczy |
| --- | --- |
| Użytkownicy, autoryzacja, lobby i czas sesji | Kompilacja i wykonanie kodu |
| Zapis zadań, starterów, testów i limitów | Uruchomienie z limitami w sandboxie |
| Sprawdzenie, czy język jest dozwolony w lobby | Wykonanie obsługiwanego języka |
| Pobranie zaufanych danych zadania do Submit | Porównanie outputów i wynik testów |
| Powiązanie submissionId ze studentem i zadaniem | Ocena i benchmark poprawnego kodu |
| Trwały zapis wyniku i budowa rankingu | Zwrócenie jednolitego wyniku |

Moduł nie zarządza bazą zadań ani uprawnieniami studentów.
Redis i Postgres z Compose są technicznymi zależnościami Judge0.

## 3. Uruchomienie lokalne

Wymagane: Docker z Compose i kontenerami Linux, JDK 21+, Maven, Python 3.
Node.js/npm potrzebujesz dodatkowo do demonstracyjnego frontendu.

Z katalogu repozytorium:

```sh
python3 scripts/prepare-local.py
docker compose -p codelobby-judge0 -f infra/docker-compose.yml -f infra/docker-compose.judge0-mac.yml up -d
```

W osobnym terminalu, nadal z katalogu repozytorium, na macOS/Linux:

```sh
set -a
. ./.env.local
set +a
cd backend
mvn spring-boot:run
```

Moduł działa domyślnie na `http://localhost:8081`, Judge0 na porcie `2358`.
Ten sam `CODELOBBY_INTERNAL_API_KEY` ustaw w głównym backendzie i wysyłaj
w nagłówku `X-CodeLobby-Internal-Key`. Klucz musi mieć minimum 32 znaki.
Nie umieszczaj go w kodzie, Git ani przeglądarce.

Na Windows instrukcja PowerShell jest w [team-quickstart.md](docs/team-quickstart.md).
Nakładka `judge0-mac` wskazuje konkretny przypięty wariant obrazu i workera.
Nazwa nie oznacza gwarancji działania na każdej platformie — sprawdź wykonanie
na swoim komputerze. Nie zmieniaj obrazu ani liczby workerów na początku integracji.

Gdy główny backend także działa w kontenerze, `localhost` oznacza ten kontener.
Skonfiguruj adres modułu osiągalny z jego sieci. Nie wystawiaj wewnętrznego API
ani Judge0 bezpośrednio studentom.

## 4. Endpointy do integracji

Każdy poniższy endpoint wymaga klucza wewnętrznego.

| Endpoint | Zastosowanie |
| --- | --- |
| `GET /internal/v1/capabilities` | Obsługiwane języki, tryby, adaptery i startery |
| `POST /internal/v1/runs` | Run na własnym wejściu; odpowiedź synchroniczna |
| `POST /internal/v1/evaluation-jobs` | Submit przyjęty do kolejki; HTTP 202 |
| `GET /internal/v1/evaluation-jobs/{submissionId}` | Pobranie stanu i wyniku Submit |
| `POST /internal/v1/evaluations` | Starszy synchroniczny Submit; do nowej integracji wybierz kolejkę |

Demonstracyjne `/api/*` nie są kontraktem dla głównego backendu. Są domyślnie
wyłączone; włącza je `CODELOBBY_DEMO_ENABLED=true` do lokalnej prezentacji.

## 5. Co wysyła student, a co przygotowuje backend

Student przesyła identyfikator zadania, kod, wybrany dozwolony język,
a przy Run również własne wejście. Backend:

1. Sprawdza użytkownika, dostęp do lobby/zadania i możliwość wysłania kodu.
2. Sprawdza język względem polityki lobby i możliwości adaptera.
3. Pobiera właściwą wersję zadania, testy, limity i adapter ze swoich danych.
4. Nadaje submissionId i zapisuje powiązanie ze studentem i wersją zadania.
5. Wywołuje wewnętrzne API modułu.

Nie przyjmuj od studenta autorytatywnego `expectedOutput`, testów ukrytych,
limitów ani dowolnego adaptera. Nie przekazuj testów ukrytych frontendowi.

## 6. Format zadania i testów

### FULL_PROGRAM

Student pisze cały program z punktem wejścia. Każdy test zawiera zwykły tekst
`input` i `expectedOutput`. Ustaw `mode: "FULL_PROGRAM"`, pomiń `adapterId`.
Limity dotyczą pojedynczego testu.

### FUNCTION

Student pisze funkcję/metodę według zarejestrowanej sygnatury. Moduł sam dodaje
zaufany kod uruchamiający. Ustaw `mode: "FUNCTION"` i `adapterId`.
Limity poprawności dotyczą całego zestawu wykonywanego w jednym procesie.

Obecne adaptery: `palindrome-length-v1` i `vowel-count-v1`.
Przyjmują pojedynczy wiersz małych liter a–z, również pusty,
i zwracają nieujemną liczbę całkowitą. Adapter palindromu zwraca **długość**,
a nie tekst palindromu. Sygnaturę dla danego języka pobierz z
`capabilities → adapters → languageProfiles` według struktury OpenAPI.

Nowy test nie wymaga zmiany silnika. Nowe zadanie z inną sygnaturą/formatem
funkcji wymaga dodania zaufanego adaptera w module. Nie wysyłaj własnego maina
w żądaniu FUNCTION. Dla dowolnego formatu tekstowego możesz użyć FULL_PROGRAM.

Przykład Submit do `POST /internal/v1/evaluation-jobs`:

```json
{
  "submissionId": "team-vowels-001",
  "language": "CPP",
  "mode": "FUNCTION",
  "adapterId": "vowel-count-v1",
  "sourceCode": "int countVowels(const string& text) { int n=0; for(char c:text) if(c=='a'||c=='e'||c=='i'||c=='o'||c=='u') ++n; return n; }",
  "limits": {
    "cpuTimeMs": 2000,
    "wallTimeMs": 5000,
    "memoryLimitBytes": 128000000
  },
  "testCases": [
    {"input": "codelobby\n", "expectedOutput": "3\n", "visibility": "PUBLIC"},
    {"input": "aeiou\n", "expectedOutput": "5\n", "visibility": "HIDDEN"}
  ]
}
```

Języki w żądaniu: `CPP`, `PYTHON`, `JAVA`. Jednostki: milisekundy i bajty.
Obecne maksima żądanych limitów: 2000 ms CPU, 5000 ms wall, 128000000 B RAM.
Wall time musi być co najmniej równy CPU time. Zestaw ma 1–50 testów;
kod do 50000 znaków, input/expectedOutput do 100000 znaków każdy.
Porównanie normalizuje końce linii i ignoruje końcowe białe znaki;
uwzględnij to przy projektowaniu zadania.

## 7. Jak obsłużyć Submit

1. Wyślij żądanie i zachowaj submissionId.
2. Po HTTP 202 odpytuj o stan co około 700–1000 ms.
3. `QUEUED` oznacza oczekiwanie, `RUNNING` ocenę.
4. `FINISHED` oznacza zakończoną ocenę. Odczytaj **`result.status`** —
   zakończenie nie musi oznaczać Accepted.
5. `FAILED` oznacza problem obsługi zgłoszenia; odczytaj `error`.
6. Zapisz wynik u siebie i udostępnij studentowi bez danych ukrytych.

Identyczny submissionId i identyczne żądanie zwracają istniejący job.
Inne dane z tym samym ID powodują HTTP 409. Nowa próba rozwiązania powinna
mieć nowe ID. Zamknięcie strony nie anuluje wykonywania zgłoszenia.

Kolejka jest w RAM: 10 oczekujących, do 100 zachowanych jobów. Wynik wygasa
30 minut po zakończeniu. Restart usuwa joby i deduplikację. Główny backend
powinien zachowywać własne zgłoszenia/wyniki i obsłużyć brak joba po restarcie.
Nie zakładaj trwałego „exactly once”.

## 8. Wyniki i błędy

Verdicty: `ACCEPTED`, `WRONG_ANSWER`, `COMPILATION_ERROR`, `RUNTIME_ERROR`,
`TIME_LIMIT_EXCEEDED`, `MEMORY_LIMIT_EXCEEDED`.

Run zwraca `{requestId, result}` z m.in. `stdout`, `stderr`, `compileOutput`,
`executionTimeMs`, `memoryBytes`. Submit używa m.in. `passedTests`,
`totalTests`, `runtimeMs`, `peakMemoryBytes`, `testResults`, `benchmark`.
Nie zamieniaj nazw pól na umowne `testsPassed/runtime/memory` bez mapowania.
Dokładne struktury i nullable pola określa OpenAPI.

Dla jawnych testów pokaż expected/actual. Dla ukrytych pokaż liczbę zaliczonych;
nie ujawniaj wejść, oczekiwanych outputów ani stderr kodu pracującego na ukrytych danych.

| HTTP | Reakcja backendu |
| --- | --- |
| 400 | Popraw dane, adapter, język lub limity |
| 401 | Sprawdź konfigurację klucza serwerowego |
| 404 | Job nie istnieje, wygasł lub został utracony po restarcie |
| 409 | To samo ID zostało użyte z inną treścią |
| 413 | Żądanie jest za duże; limit serializowanego payloadu joba to 2 MB |
| 429 | Kolejka/magazyn pełny; ponów później z tym samym ID i treścią |
| 503 | Moduł/sandbox niedostępny lub zajęty; sprawdź kod błędu odpowiedzi |

Błąd rozwiązania studenta jest wynikiem oceny, nie awarią komunikacji HTTP.

## 9. Benchmark i ranking

Submit kompiluje raz. Najpierw poprawność; dopiero po zaliczeniu wszystkich
testów: 2 rundy rozgrzewkowe i 5 pomiarów, mediana czasu i peak memory.
Kompilacja nie wchodzi do runtime. Benchmark ma osobny ograniczony budżet czasu.

W FUNCTION wewnętrzny czas mierzy głównie wywołania funkcji; peak memory
obejmuje cały proces, również interpreter/JVM. Metryka czasu jest oznaczona
`IN_PROCESS_UNTRUSTED`: kod studenta może próbować nią manipulować.
Zewnętrzny `sandboxCpuTimeMs` jest osobną metryką całego procesu benchmarku.
Nie przedstawiaj wewnętrznego czasu jako odpornego na oszustwa.

Jeżeli benchmark się nie powiedzie, poprawność może nadal być Accepted.
Do rankingu wydajności przyjmuj dopiero kompletne pomiary z ukończonego benchmarku.
Porównuj tę samą wersję zadania/testów, język, tryb i środowisko.
Czas oczekiwania na Submit jest inną wielkością niż runtime rozwiązania.

## 10. Edytor i wybór języka

Frontend może wykorzystać `frontend/src/execution-editor.js` i podłączyć
własne callbacki Run/Submit. Opis: [frontend-handoff.md](docs/frontend-handoff.md).
Przekaż mu opis zadania, startery, testy jawne i listę języków dopuszczonych
przez hosta. Jeden język oznacza zablokowany wybór; przy kilku student wybiera
z udostępnionej listy. Backend zawsze sprawdza ten wybór ponownie.

## 11. Przykład do skopiowania i sprawdzenia

`examples/team-backend` to osobna aplikacja Spring Boot na porcie 8090.
Ma własne zadanie/testy i wywołuje moduł przez HTTP bez zależności od jego klas.
Przeczytaj [README przykładu](examples/team-backend/README.md).
To demonstrator integracji, bez docelowej bazy, logowania i lobby.

Po uruchomieniu usług możesz wykonać scenariusze z katalogu głównego,
z kluczem w środowisku:

```sh
python3 scripts/verify-integration.py
python3 scripts/verify-languages.py
python3 scripts/verify-async.py
python3 scripts/verify-team-backend.py
```

Ostatni wymaga przykładowego backendu 8090. Uruchamiaj kolejno,
bez równoczesnego benchmarkowania. Te skrypty nie są audytem bezpieczeństwa.

## 12. Granice obecnego MVP

Wersja służy do lokalnego uruchamiania, prezentacji i integracji zespołowej.
Obecna blokada sandboxa i kolejka zakładają jedną instancję modułu;
worker wymaga COUNT=1. Nie zwiększaj liczby replik/workerów bez koordynacji.
Synchroniczny Run może zwrócić 503 po około 2 sekundach oczekiwania na zajęty
sandbox; job Submit pozostaje QUEUED do uzyskania dostępu.

Przypięty obraz Judge0 jest nieoficjalnym wariantem z dostosowanym workerem,
a kontenery korzystają z privileged. Nie jest to potwierdzenie gotowości do
publicznego wdrożenia. Plik Ruby `infra/judge0/isolate_job.rb` jest częścią
workera i jest podłączany przez Compose — nie instalujesz Ruby na hoście.
Integracja Piston pozostaje w repo jako alternatywa; aktualna ścieżka to Judge0.

## 13. Minimalna lista prac po stronie głównego backendu

- [ ] Uruchomić stack i przykładową integrację na swoim komputerze.
- [ ] Dodać adres modułu i klucz do konfiguracji serwerowej.
- [ ] Ustalić model zadania/testów zgodny z obsługiwanym trybem/adapterem.
- [ ] Wystawić własne, autoryzowane endpointy Run/Submit dla frontendu.
- [ ] Sprawdzać prawa użytkownika i politykę języków lobby.
- [ ] Pobierać testy/limity po stronie serwera i wywoływać wewnętrzne API.
- [ ] Zachowywać ID zgłoszenia, odbierać job i trwale zapisywać wynik.
- [ ] Obsłużyć kolejkę pełną, niedostępność usług, wygaśnięcie/restart.
- [ ] Przekazywać frontendowi bezpieczny widok wyników.

Przy sprzeczności starszych opisów korzystaj z bieżącego OpenAPI oraz
[async-submissions.md](docs/async-submissions.md),
[function-benchmark.md](docs/function-benchmark.md) i kodu modułu.
