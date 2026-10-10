# Przygotowanie zadań i testów przez zespół

Zadanie przechowuje główny backend: ID, wersję, opis, adapter/tryb, startery,
dozwolone języki, testy i limity. Moduł nie oferuje CRUD zadań ani własnej bazy.

1. Wybierz FULL_PROGRAM lub istniejący adapter FUNCTION z capabilities.
2. Dla każdego języka zachowaj zgodną sygnaturę i starter. Inny typ wejścia
   lub odpowiedzi wymaga nowego wersjonowanego adaptera w module.
3. Zbuduj 1–50 przypadków input/expectedOutput/visibility. Expected oblicza
   zaufany oracle autora zadania, nigdy rozwiązanie studenta.
4. Sprawdź puste/minimalne wejście, granice, powtórzenia i przypadki losowe ze
   stałym seedem; dodaj przypadki rozróżniające naiwne i optymalne podejścia.
5. Sprawdź rozwiązanie referencyjne i błędne dla każdego dopuszczonego języka.
6. Ustal limity z pomiarów w docelowym lokalnym środowisku, pozostaw margines.
   Ten sam limit nie oznacza tej samej trudności w różnych językach.

Obecne adaptery palindrome-length-v1 / vowel-count-v1: jeden wiersz małych
liter a–z, może być pusty; wyjście nieujemna liczba. Jedna końcowa nowa linia
jest dopuszczalna. Backend nie dodaje nagłówka batch — robi to moduł.
Porównanie normalizuje CRLF i usuwa końcowy whitespace; nie ignoruje dowolnych
różnic w środku tekstu. Zestaw i jego wersja muszą być wspólne dla porównywanych
wyników. Nie wysyłaj hidden cases do komponentu edytora.

FUNCTION: jeden proces zestawu; stan globalny pozostaje między testami/rundami.
FULL_PROGRAM: przypadki w osobnych procesach, limit per test.
Accepted z benchmark FAILED oznacza poprawność, brak metryk do rankingu.
Przerwany batch może raportować 0 passed i numer ostatnio rozpoczętego testu;
nie jest to dowód błędności wszystkich wcześniejszych odpowiedzi.
