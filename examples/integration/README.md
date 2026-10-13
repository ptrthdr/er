# Fixture integracji v1

> **WYŁĄCZNIE PRZYKŁAD / DEMONSTRATOR.** Nie jest docelową częścią aplikacji CodeLobby.
> Pliki żądań służą również jako fixture testów kontraktu.


java-run.json i java-submit.json: kompletne żądania płaskiego projektu Java
Main.java + Calc.java. vowel-evaluation.json: jeden fragment C++ w FUNCTION.
Runner wymaga klucza serwerowego. Główny backend dostarcza zaufane testy,
pliki i limity; klient przeglądarkowy nie wywołuje tego API bezpośrednio.

Testy live: scripts/verify-source-files.py oraz verify-integration.py.
Kontrakt: docs/openapi.json, migracja: docs/flat-v1-migration.md.
