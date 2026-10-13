# Przeniesienie do głównego repo

`python3 scripts/prepare-main-repo.py` przygotowuje źródła i ZIP lokalnie,
bez Git i publikacji. Runner pozostaje osobną usługą.

Paczka zawiera backend + JUnit, bibliotekę CodeMirror, infra Judge0,
OpenAPI, workflow CI, skrypty oraz fixture. Nie zawiera strony demo,
mini-backendu, sekretów ani artefaktów kompilacji.

Aktualny kontrakt: [flat-v1-migration.md](flat-v1-migration.md) i openapi.json.
sourceFiles + taskVersionId, płaski katalog, stałe punkty wejścia.
Główny backend trzeba dostosować do niekompatybilnej zmiany prototypu.
Przed scaleniem uruchom TESTING.md i sprawdź Judge0 na komputerze zespołu.
Workflow CI wymaga dopasowania ścieżek, jeśli repo ma inny układ.

Frontend osadza mountExecutionEditor i dostarcza własne callbacki do głównego
backendu. Komponent nie komunikuje się z runnerem bezpośrednio. Warstwa integracji
powinna złożyć pełne sourceFiles i wykorzystać dane taskVersionId; sama strona
demonstracyjna nadal służy pojedynczym funkcjom. Nie deklarujemy gotowego
wieloplikowego widoku frontendowego jako rezultatu migracji runnera.

PostgreSQL, JWT, lobby, kontrola plików read-only i zapis wyników są po stronie
backendu. Kolejka RAM, pojedynczy worker, uprzywilejowane kontenery oraz pomiar
FUNCTION IN_PROCESS_UNTRUSTED pozostają ograniczeniami lokalnego MVP.
