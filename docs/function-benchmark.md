# Pomiar rozwiązania — protokół FUNCTION

Nowy sterownik FUNCTION wczytuje cały zestaw przed pomiarem. Ocena poprawności
wykonuje go raz. Dopiero po porównaniu wyników worker uruchamia osobny proces
benchmarkowy z tym samym artefaktem: 2 rozgrzewki i 5 pomiarów w jednej JVM,
interpreterze lub procesie C++. Przygotowanie danych i wypisanie wyników są
poza pomiarem. Każda runda musi dać identyczne wyniki, worker porównuje wyjście
z expectedOutput. Kod jest nadal kompilowany raz na Submit.

C++ używa steady_clock, Python perf_counter_ns, Java System.nanoTime.
To czas upływający wywołań funkcji, nie czas CPU. Obejmuje operacje algorytmu
oraz pętlę sterownika zapisującą wyniki. Nie usuwa wpływu GC, JIT ani planisty.
Pole benchmark.measurementScope = FUNCTION_CALLS_ELAPSED, measurementTrust =
IN_PROCESS_UNTRUSTED. medianRuntimeMs zachowuje części ułamkowe; starsze
runtimeMs jest zaokrąglane do całych milisekund. sandboxCpuTimeMs to zewnętrzny
czas CPU całego procesu benchmarku, obejmującego wszystkie siedem rund i start
środowiska. peakMemoryBytes obejmuje cały ten proces, także rozgrzewki.

Limity sandboxa obowiązują cały proces benchmarkowy ze wszystkimi rundami.
Jeśli benchmark przekroczy limit, poprawność pozostaje Accepted, ale benchmark
jest FAILED i nie dostarcza metryk do rankingu. Nie podnosimy automatycznie
limitów. Stan funkcji utrzymuje się między testami oraz rundami.

FULL_PROGRAM zachowuje dotychczasowy zewnętrzny pomiar CPU procesów.
Run również zachowuje dotychczasowy pomiar zewnętrzny.

Kod studenta może ingerować w zegary, stdout/stderr i sterownik tego samego
procesu. Worker sprawdza format pięciu nieujemnych skończonych pomiarów i
zgodność wyników, ale nie dowodzi autentyczności zgłoszonego czasu.
Wewnętrzna metryka nadaje się do lokalnej prezentacji i zaufanych eksperymentów;
nie wolno traktować jej jako odpornej na oszustwa podstawy publicznego rankingu.
Sandbox pozostaje niezależną kontrolą limitów. Przed publicznym rankingiem
potrzebne są zewnętrzne, odporne na manipulacje pomiary lub audyt sterownika.

Zmiana wymaga zgodnego backendu i poprawki workera. Po uruchomieniu nowego
backendu odtwórz worker, aby załadował zamontowany isolate_job.rb. Nie wykonano
jeszcze regresji tej wersji ani porównania wyników pomiarów.

## Osobny budżet benchmarku FUNCTION

Budżet poprawności pozostaje bez zmian. Benchmark otrzymuje CPU:
min(7 × limit zadania + 1 s, 30 s), wall:
min(7 × limit zadania + 2 s, 60 s). RAM i procesy pozostają bez zmian.
Budżet obejmuje wszystkie rozgrzewki i pomiary, nie zmienia limitu poprawności.
Dotychczasowy opis jednego identycznego limitu obu faz jest nieaktualny.

Sterownik poprawności emituje przed wywołaniem numer testu na stderr.
Przy TLE/awarii worker zwraca w message ostatni numer rozpoczętego testu,
bez jego wejścia i oczekiwanej odpowiedzi. Wcześniejsze wyniki pozostają
niedostępne w partii; 0/50 nadal nie oznacza błędności wszystkich przypadków.
Numer jest diagnostyką wewnątrz niezaufanego procesu i może zostać zmanipulowany;
brak znacznika oznacza brak wiarygodnego wskazania. Wrong Answer przy poprawnym
zakończeniu nadal ma wyniki per test. Nie ujawniamy stderr testów ukrytych.
