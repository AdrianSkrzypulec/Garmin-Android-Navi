# Garmin Maps Navi — Nawigacja Google Maps & Mapy.com (Mapy.cz) na Garmin vívoactive 4

Kompletny system nawigacyjny przenoszący wskazówki turn-by-turn z aplikacji **Google Maps** oraz **Mapy.com (dawniej Mapy.cz)** na telefonie z systemem Android wprost na ekran zegarka **Garmin vívoactive 4**.

System składa się z dwóch współpracujących modułów:
1. **Aplikacja na zegarek Garmin (`garmin/`)** – napisana w języku Monkey C dla Connect IQ, rysująca kontrastowe, wektorowe symbole manewrów (strzałki skrętu, ronda z numerami zjazdów, zawracanie), odległość, ulicę, czas dojazdu (ETA) oraz generująca wibracje haptyczne przed manewrem.
2. **Aplikacja towarzysząca na telefon Android (`android/`)** – usługa w tle przechwytująca powiadomienia Google Maps oraz Mapy.com (Mapy.cz) w czasie rzeczywistym (w języku polskim i angielskim) i transmitująca je bezprzewodowo przez Bluetooth do zegarka za pomocą Garmin Connect IQ Mobile SDK.

---

## 📸 Funkcjonalności na zegarku vívoactive 4

Ekran zegarka (okrągły 260 x 260 px MIP, doskonała czytelność w pełnym słońcu):
- **Wektorowe strzałki kierunkowe o wysokim kontraście**:
  - `STRAIGHT` – jazda prosto
  - `TURN_LEFT` / `TURN_RIGHT` – skręt pod kątem 90° w lewo / prawo
  - `SLIGHT_LEFT` / `SLIGHT_RIGHT` – łagodny skręt (45°)
  - `SHARP_LEFT` / `SHARP_RIGHT` – ostry zakręt (135°)
  - `U_TURN` – zawracanie (pętla nawrotu)
  - `ROUNDABOUT` – piktogram ronda z **dokładnym numerem zjazdu** (np. zjazd 1, 2, 3...)
  - `KEEP_LEFT` / `KEEP_RIGHT` – rozwidlenie drogi / trzymanie pasa
  - `DESTINATION` – flaga osiągnięcia celu podróży
  - `STANDBY / BRAK TRASY` – czytelny ekran oczekiwania z kompasem
- **Wielki wskaźnik odległości**: (np. `150 m`, `2.4 km`) widoczny z daleka podczas jazdy rowerem, biegu czy prowadzenia pojazdu.
- **Ulica docelowa**: nazwa ulicy lub kierunek trasy dopasowany do okrągłego ekranu.
- **Pasek podróży**: godzina przybycia (ETA) oraz pozostały dystans całkowity (np. `14:45 • 4.2 km`).
- **Wibracje haptyczne**: zegarek wibruje na nadgarstku przy zmianie manewru, informując Cię o zbliżającym się zakręcie bez konieczności ciągłego patrzenia na ekran.

---

## 📁 Struktura Repozytorium

```
/Users/adrian/Navi/
├── garmin/                          # Projekt Connect IQ dla Garmin vívoactive 4
│   ├── manifest.xml                 # Manifest Connect IQ z UUID i uprawnieniami
│   ├── monkey.jungle                # Konfiguracja źródeł i zasobów
│   ├── build_prg.sh                 # Skrypt kompilacji aplikacji dla vívoactive 4
│   ├── resources/
│   │   ├── drawables/               # Ikona aplikacji i grafiki
│   │   └── strings/strings.xml      # Teksty i komunikaty
│   └── source/
│       ├── NaviApp.mc               # Cykl życia aplikacji i rejestracja Bluetooth
│       ├── NaviView.mc              # Główny widok UI zoptymalizowany dla 260x260 px
│       ├── NaviGlanceView.mc        # Podgląd Glance w karuzeli widgetów zegarka
│       ├── NaviModel.mc             # Model danych i wyzwalanie wibracji
│       ├── NaviDelegate.mc          # Obsługa dotyku i przycisków vívoactive 4
│       └── ArrowRenderer.mc         # Wektorowe rysowanie piktogramów i strzałek
│
├── android/                         # Aplikacja towarzysząca Android (Kotlin)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml  # Uprawnienia BT i usługa NotificationListener
│   │   │   ├── java/com/navi/garmin/
│   │   │   │   ├── MainActivity.kt           # Ekran główny z panelem i symulatorem
│   │   │   │   ├── MapsNotificationService.kt # Przechwytywanie z Google Maps
│   │   │   │   ├── ManeuverParser.kt         # Inteligentny parser PL / EN
│   │   │   │   ├── GarminBridge.kt           # Mostek Connect IQ Mobile SDK
│   │   │   │   ├── ManeuverType.kt           # Typy manewrów
│   │   │   │   └── NavigationData.kt         # Model danych przesyłany do zegarka
│   │   │   └── res/                          # Układ UI, kolory i style Material 3
│   │   └── src/test/                         # Testy jednostkowe parsera manewrów
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── README.md
```

---

## 🚀 Krok 1: Wdrożenie aplikacji na zegarek Garmin vívoactive 4

### Metoda A: Za pomocą Visual Studio Code (Zalecana)
1. Zainstaluj w Visual Studio Code oficjalne rozszerzenie **Monkey C** firmy Garmin.
2. Wciśnij `Ctrl+Shift+P` (lub `Cmd+Shift+P` na macOS) i wybierz:
   `Monkey C: Verify Installation` oraz pobierz najnowsze Connect IQ SDK za pomocą **SDK Managera**.
3. Otwórz folder `garmin/` w VS Code.
4. Wciśnij `Ctrl+Shift+P` -> `Monkey C: Build for Device`.
5. Jako urządzenie wybierz **vivoactive 4**.
6. Kompilator wygeneruje plik wykonywalny `NaviApp.prg`.

### Metoda B: Bezpośrednie wgranie pliku na zegarek (Sideloading przez USB)
1. Podłącz zegarek **Garmin vívoactive 4** kablem USB do komputera.
2. Zegarek pojawi się jako dysk zewnętrzny `GARMIN`.
3. Skopiuj skompilowany plik `NaviApp.prg` bezpośrednio do katalogu:
   ```
   GARMIN/APPS/
   ```
4. Bezpiecznie odłącz zegarek od komputera.
5. Aplikacja **Maps Navi** pojawi się natychmiast na liście aplikacji w menu zegarka!

---

## 📱 Krok 2: Uruchomienie aplikacji na telefonie z systemem Android

1. Otwórz folder `android/` w **Android Studio** (Hedgehog, Iguana lub nowszym).
2. Pobierz bibliotekę **Connect IQ Mobile SDK for Android** ze strony [Garmin Developer Portal](https://developer.garmin.com/connect-iq/sdk/) i umieść plik `connectiq-sdk-*.aar` lub `.jar` w folderze `android/app/libs/`.
3. Podłącz telefon z Androidem przez USB z włączonym debugowaniem USB (lub wybierz emulator).
4. Kliknij zielony przycisk **Run 'app'** (`Shift + F10`).

### Nadanie uprawnień w telefonie:
1. Po uruchomieniu aplikacji zobaczysz czerwony komunikat informujący o braku dostępu do powiadomień.
2. Kliknij przycisk **"Nadaj dostęp do powiadomień"**.
3. W ustawieniach systemowych Androida włącz przełącznik obok aplikacji **Garmin Maps Navi**.
4. Wróć do aplikacji – status zmieni się na zielony: **"Dostęp do powiadomień: AKTYWNY"**.

---

## 🧪 Krok 3: Testowanie bez wychodzenia z domu (Symulator na biurku)

Aplikacja Android posiada wbudowany **Symulator Manewrów**:
1. Uruchom aplikację **Maps Navi** na zegarku vívoactive 4.
2. W aplikacji Android kliknij przycisk:
   - **Prosto (500m)** – na zegarku pojawi się zielona pionowa strzała i "500 m".
   - **W lewo (150m)** – zegarek delikatnie zawibruje i pokaże strzałkę w lewo oraz ulicę.
   - **Rondo 2. zjazd (100m)** – na zegarku pojawi się piktogram ronda z cyfrą 2 w środku.
   - **Dotarto do celu** – pojawi się flaga mety.
   - **Reset (Standby)** – zegarek powróci do ekranu oczekiwania.

---

## 🧭 Krok 4: Używanie z Google Maps / Mapy.com na co dzień

1. Uruchom aplikację **Maps Navi** na zegarku.
2. Otwórz **Google Maps** lub **Mapy.com (Mapy.cz)** na telefonie i rozpocznij nawigację do dowolnego celu.
3. Wskazówki będą przesyłane na zegarek w czasie rzeczywistym:
   - Każda zmiana manewru wywoła krótki impuls wibracyjny na nadgarstku.
   - Odległość na zegarku będzie płynnie maleć w miarę zbliżania się do manewru.
   - Po dotarciu do celu lub wyłączeniu nawigacji w telefonie zegarek automatycznie powróci do stanu czuwania.

