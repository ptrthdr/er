# Backend ↔ runner — kontrakt v1 (płaski projekt)

Źródło prawdy: [openapi.json](openapi.json). Wdrożenie specyfikacji
[runner-flat-proposal.md](runner-flat-proposal.md); ten plik jest opisem pomocniczym.

## Żądania

Run: POST /internal/v1/runs — requestId, taskVersionId, language, mode,
sourceFiles, stdin, limits, opcjonalnie adapterId dla FUNCTION.
Submit: POST /internal/v1/evaluation-jobs — submissionId, taskVersionId,
language, mode, sourceFiles, testCases, limits, opcjonalnie adapterId.
Główny backend dostarcza kompletny snapshot zadania, nie identyfikator do pobrania
z bazy. Runner nie zna użytkowników ani lobby.

sourceFiles: lista `{path, content}`. Nie ma aliasów sourceCode, files/name,
taskId/taskVersion ani entryPoint. Są odrzucane jako nieobsługiwane pola.
Dotychczasowe konstruktory Java prototypu to lokalna pomoc dla demonstratora,
nie aliasy HTTP. Synchroniczne /evaluations pozostaje legacy, ale przyjmuje
aktualny model danych. Nowy backend używa kolejki.

## Pliki i wykonanie

1–20 zwykłych plików, jeden katalog, do 131072 bajtów UTF-8 łącznie.
Nazwy: `[A-Za-z_][A-Za-z0-9_]*` + rozszerzenie, max 120 znaków.
Bez separatorów, spacji, znaków powłoki i kolizji case-insensitive.
C++: main.cpp, inne .cpp oraz .h/.hpp; -std=c++17 -O2, wszystkie .cpp.
Java: Main.java, inne .java; wszystkie kompilowane, uruchamiana klasa Main.
Python: main.py i inne .py, importy płaskich modułów. Nie instalujemy zależności.
Nie obsługujemy katalogów i pakietów Javy. To nie jest dowolny system budowania.
Archiwum dodatkowych plików tworzy runner z walidowanych danych;
klient nie może dostarczyć ZIP-a, skryptu budowania lub flag.

FUNCTION: dokładnie jeden plik fragmentu funkcji/metody, adapterId obowiązkowy,
sterownik generuje runner. Zachowane adaptery palindrome-length-v1 i vowel-count-v1;
sygnatury i startery w capabilities. Wieloplikowy FUNCTION nie jest obsługiwany.

## Jednostki i limity

Czas w ms, pamięć w bajtach. Limity wykonania maksymalnie takie jak konfiguracja
Judge0; odczytaj capabilities. FULL_PROGRAM: per test. FUNCTION: cały zestaw.
Body <=2000000 bajtów (Run i Submit), odrzucenie 413 przed parserem JSON,
również bez Content-Length. Input/expectedOutput <=100000 bajtów UTF-8.
Testy 1–50. stdout/stderr <=65536 bajtów na wykonanie; przekroczenie daje
OUTPUT_LIMIT_EXCEEDED, nigdy Accepted dzięki obcięciu. Strumienie przechwytuje bounded worker; overflow zamyka strumień, a program
ignorujący błąd nadal podlega limitom CPU/wall. Limit pliku tymczasowego
w sandboxie wynosi osobno 1 MiB.
Log kompilacji w odpowiedzi
ograniczony do 64 KiB; limit zapisu kompilatora dodatkowo egzekwuje Isolate.

## Joby i ponowienia

Nowy job: 202 QUEUED. Replay identycznego aktywnego joba: 202 i stan aktualny.
Replay zakończonego FINISHED/FAILED: 200. Inne dane z tym samym submissionId: 409.
submissionId ma 1–128 znaków URL-safe (litery/cyfry oraz . _ : -), aby można było pobrać job po ścieżce.
Fingerprint sortuje sourceFiles po path; treść plików nie jest normalizowana.
Kolejność testów ma znaczenie. Wersja zadania, limity, język i tryb należą do fingerprintu.
Brak adapterId i null są równoważne po deserializacji do modelu.
GET /evaluation-jobs/{submissionId}: 200 lub 404; nieprawidłowy format ID daje 400.
Odpowiedzi POST /evaluation-jobs (202 i replay 200) zawierają Location z adresem do odpytywania joba.
FINISHED jest zakończeniem oceny, verdict w result.status; FAILED to problem obsługi.
Pamięć: 1 wątek, 10 oczekujących, 100 jobów, 30 minut po zakończeniu.
Restart usuwa joby/deduplikację. PostgreSQL oraz recovery/retry należą do głównego backendu.

## Wyniki i prywatność

Run `{requestId,result}`; Submit snapshot joba. Publiczne testy zawierają
expected/output/stderr, ukryte mają null. Globalne komunikaty nie przekazują
kontrolowanej przez studenta diagnostyki ukrytych testów. Backend dodatkowo
agreguje wyniki ukryte przed wysłaniem do użytkownika.
Benchmark: 2 warmupy, 5 pomiarów; kompilacja raz, poprawność przed benchmarkiem.
FULL_PROGRAM: mediana sum czasów CPU procesów w rundzie, maksymalny per-process
peak memory w mierzonych rundach. FUNCTION: wewnętrzny elapsed wywołań,
IN_PROCESS_UNTRUSTED, osobny zewnętrzny sandboxCpuTimeMs.
Accepted z benchmark FAILED nie ma metryk do rankingu. Nie mieszaj języków,
wersji zadań, trybów i środowisk w jednym rankingu.

## Błędy i dostęp

Format `{code,message,retryable}` dla błędów HTTP oraz błędu joba.
400 INVALID_REQUEST/INVALID_SOURCE_FILES, 401 UNAUTHORIZED, 404 JOB_NOT_FOUND,
409 SUBMISSION_ID_CONFLICT, 413 PAYLOAD_TOO_LARGE, 429 QUEUE_CAPACITY_EXCEEDED,
503 EXECUTION_UNAVAILABLE. 429 i przejściowe 503 retryable=true.
Nieistniejący endpoint daje 404 RESOURCE_NOT_FOUND, zła metoda 405 METHOD_NOT_ALLOWED
(z nagłówkiem Allow), nieobsługiwany Accept 406 NOT_ACCEPTABLE, a Content-Type
415 UNSUPPORTED_MEDIA_TYPE. Nieprzewidziany błąd daje 500 INTERNAL_SERVER_ERROR
z retryable=false. Odpowiedź błędu jest JSON-em także przy nieobsługiwanym Accept.
Walidacja wskazuje nazwy błędnych pól, bez zwracania kodu źródłowego lub wartości pól.
Szczegóły wyjątków i odpowiedzi sandboxa nie trafiają do komunikatów błędów HTTP.
Błędy rozwiązania to result.status, nie błąd HTTP.
X-CodeLobby-Internal-Key min 32 znaki, tylko między serwerami. Nie wystawiać
runnera/Judge0 publicznie. Między hostami sieć prywatna i TLS.
capabilities pobiera faktyczne nazwy wersji z Judge0; cache w procesie runnera.
Po zmianie obrazu/rejestrowanych wersji restart runnera odświeża cache.

Instrukcje: [backend-handoff.md](backend-handoff.md), [TESTING.md](TESTING.md).
