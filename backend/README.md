# CodeLobby Execution API

Spring Boot obsługuje C++, Python i Javę przez Judge0. Domyślny port: 8081.
Piston jest zachowany jako opcjonalny wariant porównawczy.

- [Uruchomienie](../docs/local-development.md)
- [Przekazanie backendowi](../docs/backend-handoff.md)
- [Kontrakt API](../docs/integration-contract.md)
- [Języki i sygnatury](../docs/languages.md)

API `/internal/v1` przyjmuje kod, zaufane limity i testy. Wymaga serwerowego
klucza CODELOBBY_INTERNAL_API_KEY co najmniej 32 znaków.
Tryby: FULL_PROGRAM (pełny program) i FUNCTION (funkcja z adapterem).
Submit automatycznie mierzy poprawne rozwiązanie: 2 rozgrzewki i 5 pomiarów,
jedna kompilacja. Tryb FUNCTION wykonuje wszystkie przypadki w jednym procesie
na rundę; FULL_PROGRAM używa osobnych procesów dla przypadków.

Przykładowe `/api` są wyłączone domyślnie. CODELOBBY_DEMO_ENABLED=true włącza
palindrom, Run, Submit i pomocniczy endpoint benchmarku. Zadanie z pamięci
jest demonstratorem, nie bazą zadań. Główny backend odpowiada za zadania,
politykę języków lobby, uprawnienia i przechowywanie wyników.

Worker infra/judge0/isolate_job.rb musi być zgodny z używanym obrazem Judge0.
