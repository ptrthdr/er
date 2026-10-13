# Weryfikacja przygotowania do głównego repo — 2026-10-12

Środowisko lokalne: macOS Apple Silicon, Docker Desktop, przypięty worker
Judge0, JDK 25 z targetem Java 21. Niczego nie opublikowano.

| Sprawdzenie | Wynik |
| --- | --- |
| Jednostkowe JUnit (6 klas) | 48/48 |
| Integracyjne Spring Boot z mockiem klienta Judge0 | 7/7 |
| Rzeczywiste API + Judge0 | 17/17 |
| Python/Java FUNCTION i FULL_PROGRAM | 8/8 |
| Rzeczywista kolejka Submit | 12/12 |
| Testy JUnit kopii do głównego repo | 55/55 |
| Build biblioteki edytora z kopii do głównego repo | poprawny |
| Build dotychczasowego frontendowego demo | poprawny |
| Build opcjonalnego profilu backendowego demo | poprawny |
| Brak trzech klas demonstratora w domyślnym JAR | potwierdzony |

Testy API obejmowały Run/Submit, adaptery, błędne dane, wrong answer,
compilation/runtime error, TLE, MLE i redakcję danych testów ukrytych.
Testy kolejki obejmowały przyjęcie 202, deduplikację, konflikt ID, stany,
benchmark, walidację przed przyjęciem i przepełnienie 429.

W pierwszym przebiegu testów Spring rozpoznawanie ścieżki filtra wymagało
poprawki dla pustego servletPath. Po poprawce testy uwierzytelnienia przechodzą.
Mockito skonfigurowano jako subclass mock maker, bez dynamicznego agenta JVM.
Test kolejki powtórzono po zakłóceniu serwera przez przebudowę jego pliku JAR;
końcowy przebieg używał niezmiennej kopii testowego artefaktu i przeszedł.

Nie wykonano nowego audytu izolacji, prób obciążeniowych wielu replik,
pokrycia wszystkich ścieżek ani ponownych testów przeglądarkowych.
Build frontendu nie zastępuje jego testów zachowania w przeglądarce.
Kolejka nadal jest w RAM, a wewnętrzny zegar funkcji nie jest odporny na manipulację.
