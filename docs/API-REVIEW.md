# Przegląd API runnera

Repo ma już dobry punkt wyjścia: osobne wewnętrzne API, klucz serwerowy,
limity żądań, walidację plików, kolejkę i testy. Nie ma potrzeby zmieniać
technologii ani dodawać kolejnych usług na tym etapie.

## Poprawki w tym PR

- Błędy Spring MVC, np. zła metoda HTTP lub Content-Type, mają teraz ten sam
  format `{code, message, retryable}` co pozostałe błędy API. Zachowane są
  właściwe statusy i nagłówki, w tym Allow.
- Nieprzewidziany wyjątek daje bezpieczne 500. Komunikat nie zawiera stack trace,
  treści kodu ani odpowiedzi sandboxa. Szczegóły wyjątku są w logach serwera.
- Niepowodzenie asynchronicznego joba jest logowane z jego ID, żeby można było
  znaleźć przyczynę bez ujawniania jej w odpowiedzi API.
- Walidacja podaje nazwy błędnych pól. ID w adresie pobierania joba ma taką
  samą walidację jak submissionId przy jego tworzeniu.
- Filtry korzystają ze wspólnego zapisu błędów i skonfigurowanego ObjectMappera.
  Klucz złożony wyłącznie ze spacji jest traktowany jak brak konfiguracji.
- Odpowiedź na utworzenie lub ponowienie joba ma Location z adresem do odpytywania.
- Poprawione jest składanie odpowiedzi, gdy brak metryk benchmarku. Zagnieżdżony
  operator warunkowy wymuszał zamianę null na long i wywracał cztery istniejące
  testy na main. Brak pomiaru pozostaje null; zasady oceniania i benchmarku są takie same.
- OpenAPI i opis integracji uwzględniają te odpowiedzi. Nowe testy sprawdzają
  rzeczywiste żądania HTTP, statusy, nagłówki i brak ujawniania szczegółów wyjątku.

Zmiany nie obejmują workera Judge0, obrazów Dockera, ustawień Maca, adapterów,
kompilatorów ani limitów wykonania.

## Sugestie przed integracją z CodeLobby

1. **Uzgodnić, co dokładnie robi Run.** Wewnętrzny endpoint runs przyjmuje jeden
   stdin. Metoda runPublicTests istnieje, ale nie ma dla niej wersjonowanego
   endpointu. Jeśli przycisk ma sprawdzać wszystkie testy jawne, potrzebny jest
   wspólny kontrakt z backendem.
2. **Dopasować format zadań hosta do runnera.** Obecne FULL_PROGRAM przyjmuje
   płaskie pliki i stałe punkty wejścia. FUNCTION wymaga zarejestrowanego adaptera;
   samo dodanie dowolnej sygnatury lub testów JUnit w zadaniu nie wystarczy.
   To trzeba uzgodnić z modelem Task i plików w głównym backendzie, zanim powstanie
   upload zadań. Na start można wybrać jeden prosty format dla Javy.
3. **Zachować zgłoszenia po stronie głównego backendu.** Kolejka runnera jest w RAM
   i znika po restarcie. Backend powinien zapisać submission przed wysyłką oraz
   ustalić, co robi przy 404 lub zerwanym połączeniu. Nie trzeba teraz przebudowywać
   kolejki ani dokładać Redis/Kafki.
4. **Uporządkować pozostałości po demo.** Część instrukcji nadal opisuje profil
   -Pdemo i demonstracyjny backend, których nie ma w tej paczce. Warto zostawić
   jedną aktualną ścieżkę uruchomienia, a historyczne instrukcje oznaczyć osobno.

Testy JUnit/Spring i walidacja OpenAPI nie zastępują sprawdzenia z prawdziwym
Judge0 na Macu. To pozostaje osobnym sprawdzeniem przed integracją.
