# Weryfikacja integracji przez backendowca

Uruchom Judge0 i Spring Boot według `docs/backend-handoff.md`. Potrzebny jest
Python 3; skrypt korzysta wyłącznie z biblioteki standardowej.

W terminalu ustaw `CODELOBBY_INTERNAL_API_KEY` na ten sam sekret, który zna
backend. Nie zapisuj wartości w repozytorium. Następnie z katalogu projektu:

```sh
python3 scripts/verify-integration.py
```

Opcjonalnie ustaw `CODELOBBY_API_URL` (domyślnie http://127.0.0.1:8081)
i `CODELOBBY_VERIFY_REPORT` na ścieżkę raportu JSON. Skrypt nie wypisuje klucza,
nie potrzebuje frontendu ani trybu demo. Wykonuje prawdziwe kompilacje i pomiary
w sandboxie, dlatego uruchamiaj go przy wolnym workerze.

Sprawdzenia obejmują możliwości API, uwierzytelnienie, Run/Submit obu adapterów
i pełnego programu, metryki i liczbę powtórzeń benchmarku, ukrywanie wyników,
błędne dane i limity, nieznany adapter, WA, CE, RE, TLE i MLE.
Kod wyjścia 0 oznacza, że wszystkie sprawdzenia przeszły; 1 oznacza błąd.

`vowel-evaluation.json` jest gotowym przykładem poprawnego Submit. Oczekiwany
wynik: HTTP 200, submissionId team-vowels-001, ACCEPTED, passedTests 3,
totalTests 3, benchmark COMPLETED z 2 rozgrzewkami i 5 pomiarami.
Czas i pamięć zależą od środowiska; nie porównuj ich z dokładnymi stałymi.

Skrypt zakłada limity demonstracyjne 2 s CPU, 5 s wall, 128 MB i co najmniej
3 testy. Dla innej polityki limitów dostosuj dane zgodnie z kontraktem.
Sam wynik tych sprawdzeń nie stanowi audytu bezpieczeństwa sandboxa ani testu
wielu równoczesnych studentów.
