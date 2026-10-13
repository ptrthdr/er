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
