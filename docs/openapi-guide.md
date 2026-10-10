# Kontrakt integracyjny OpenAPI

Plik `docs/openapi.json` to OpenAPI 3.1 dla pięciu ścieżek `/internal/v1`:
capabilities, Run, synchroniczne evaluation, kolejka evaluation-jobs i status.
Demo `/api` nie jest kontraktem integracyjnym zespołu. Plik można zaimportować
w narzędziu zespołu (np. Swagger Editor lub Postman) bez udostępniania sekretu.
API key jest konfigurowany wyłącznie po stronie serwerów.

Opisano jednostki, null, ograniczenia formatu, metryki, widoczność testów,
deduplikację, pamięciowy charakter kolejki i sposób ponawiania.
400 oznacza dane wymagające poprawienia; 409 konflikt ID; 413 zbyt dużą treść;
429 oznacza brak przyjęcia i możliwość ponowienia tym samym ID później.
404 nie rozróżnia nieistniejącego, wygasłego i utraconego po restarcie zgłoszenia.
FAILED po przyjęciu wymaga świadomej decyzji backendu: identyczne ID odtwarza
wynik, nowe ID oznacza nową ocenę. Nie zapewniamy trwałego exactly-once.

## Sprawdzenie

```sh
python3 -m venv /tmp/codelobby-contract-check
/tmp/codelobby-contract-check/bin/pip install -r scripts/contract-requirements.txt
CODELOBBY_API_URL=http://127.0.0.1:8081 /tmp/codelobby-contract-check/bin/python scripts/verify-contract.py
```

W środowisku ustaw CODELOBBY_INTERNAL_API_KEY zgodny z uruchomionym backendem.
Skrypt waliduje OpenAPI oraz przykład, sprawdza schematy odpowiedzi działającego
API, walidację przed przyjęciem, deduplikację i konflikt. Uruchamia rzeczywisty
Run trwający 3 sekundy oraz Submit; potwierdza oczekiwanie na sandbox w QUEUED.
Nie uruchamiaj innych obciążających zadań podczas sprawdzania.

Zmiany kontraktu powinny przechodzić ten skrypt. Schemat nie zastępuje
walidacji semantycznej: maksymalne limity zależą od konfiguracji, a sygnatury
od adaptera. Przed zmianą breaking zachowaj v1 lub wprowadź nową wersję.
