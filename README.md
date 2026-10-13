# CodeLobby — moduł wykonawczy do integracji

Zawiera serwis wykonawczy, bibliotekę edytora, Judge0 i testy.
Nie zawiera demonstracyjnej strony ani mini-backendu.

Zacznij od [przewodnika dla backendowca](docs/BACKEND-REVIEW.md),
[instrukcji integracji](docs/MAIN-REPO-INTEGRATION.md)
i [Dla BACKENDOWCA](Dla%20BACKENDOWCA.md).

Testy: `mvn -f runner/pom.xml clean test`.
Edytor: `npm --prefix components/code-editor ci` i `npm --prefix components/code-editor run build`.
Sandbox i uruchomienie serwisu: docs/team-quickstart.md — używaj
trybu bez demo, czyli `mvn spring-boot:run` bez `-Pdemo` i bez flagi demo.
Instrukcje demonstratorów w pozostałych dokumentach odnoszą się
wyłącznie do pełnego repo modułu, nie do tej paczki.

## Co należy do modułu, a co jest przykładem

| Katalog | Rola |
| --- | --- |
| `runner/` | **Moduł docelowy** - wykonywanie i ocena kodu, wraz z testami |
| `components/code-editor/` | **Moduł docelowy** - komponent edytora |
| `infra/` | **Konfiguracja środowiska** - uruchomienie Judge0 |
| `docs/openapi.json` i instrukcje integracji | **Kontrakt i dokumentacja modułu** |
| `.github/workflows/` | **Automatyczne sprawdzanie projektu** |
| `scripts/` | **Narzędzia pomocnicze** - konfiguracja i weryfikacja, nie kod obsługi żądań |
| `examples/` | **WYŁĄCZNIE PRZYKŁADY** - zadania, rozwiązania i żądania API |
| `docs/verification/` | **Raporty pomocnicze** - historia wykonanych sprawdzeń |

Przykłady nie są katalogiem zadań aplikacji ani głównym backendem CodeLobby.
Nie trzeba ich wdrażać jako części działającej usługi. Niektóre pliki
`examples/integration/` są jednak używane przez testy kontraktu i workflow CI.
Usuwając je z repo, trzeba odpowiednio zmienić te testy.

