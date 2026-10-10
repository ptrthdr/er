# Środowisko Judge0

Instrukcja uruchomienia: [local-development.md](../docs/local-development.md).
Judge0 jest domyślnym silnikiem modułu. Compose obejmuje Judge0, pojedynczego
workera, Redis i Postgres; frontend i Spring Boot startują osobno.

Bazowy Compose zachowuje oficjalny obraz judge0/judge0:1.13.1. Na używanym
Docker Desktop Apple Silicon wystąpiła niezgodność cgroups v1/v2.
Nakładka docker-compose.judge0-mac.yml zachowuje działający lokalny wariant
mrkushalsm/judge0, teraz przypięty digestem odczytanym z lokalnego obrazu.
Przypięcie zapewnia stałą zawartość obrazu, nie potwierdza jego bezpieczeństwa
ani zgodności na każdym komputerze. To nieoficjalny wariant amd64.

Wymagany worker judge0/isolate_job.rb kompiluje raz, rozdziela kompilację i
wykonanie oraz obsługuje protokół testów i automatycznego benchmarku.
Zmiana obrazu wymaga sprawdzenia zgodności poprawki. Pozostaw COUNT=1 ze względu
na stałą rezerwację identyfikatorów Isolate.

Port API jest związany z localhost:2358. Kontenery server/worker wymagają
uprzywilejowanego trybu. Ta konfiguracja jest środowiskiem lokalnego rozwoju,
nie gotową instrukcją publicznego wdrożenia.

Pomiary dotyczą CPU i szczytu pamięci procesu wykonawczego, obejmują sterownik
oraz interpreter/JVM, pomijają kompilację. Wersje i ograniczenia:
[languages.md](../docs/languages.md), [function-evaluation.md](../docs/function-evaluation.md).
