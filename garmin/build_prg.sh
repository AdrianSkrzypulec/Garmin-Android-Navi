#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "====================================================="
echo "   Garmin vívoactive 4 - Kompilacja NaviApp.prg"
echo "====================================================="

# 1. Poszukiwanie kompilatora monkeyc
MONKEYC=""
if command -v monkeyc >/dev/null 2>&1; then
    MONKEYC="monkeyc"
elif [ -n "$CONNECTIQ_HOME" ] && [ -x "$CONNECTIQ_HOME/bin/monkeyc" ]; then
    MONKEYC="$CONNECTIQ_HOME/bin/monkeyc"
else
    # Przeszukaj standardowe ścieżki instalacji SDK na macOS
    POSSIBLE_SDKS=$(find "$HOME/Library/Application Support/Garmin/ConnectIQ/Sdks" -name "monkeyc" 2>/dev/null | sort -r | head -n 1 || true)
    if [ -n "$POSSIBLE_SDKS" ] && [ -x "$POSSIBLE_SDKS" ]; then
        MONKEYC="$POSSIBLE_SDKS"
    fi
fi

if [ -z "$MONKEYC" ]; then
    echo "❌ BŁĄD: Nie znaleziono kompilatora 'monkeyc' firmy Garmin."
    echo ""
    echo "💡 Jak to naprawić:"
    echo " 1. Otwórz Visual Studio Code."
    echo " 2. Zainstaluj rozszerzenie 'Monkey C'."
    echo " 3. Wciśnij Cmd+Shift+P -> 'Monkey C: Verify Installation' i pobierz SDK."
    echo " lub ustaw zmienną środowiskową CONNECTIQ_HOME wskazującą na folder SDK."
    exit 1
fi

echo "✅ Znaleziono kompilator Monkey C: $MONKEYC"

# 2. Sprawdzanie / generowanie klucza deweloperskiego (RSA DER)
KEY_FILE="$SCRIPT_DIR/developer_key.der"
if [ ! -f "$KEY_FILE" ]; then
    if [ -f "$HOME/.garmin/developer_key.der" ]; then
        KEY_FILE="$HOME/.garmin/developer_key.der"
        echo "✅ Użyto istniejącego klucza: $KEY_FILE"
    else
        echo "🔑 Generowanie nowego klucza deweloperskiego (developer_key.der)..."
        openssl genrsa -out "$SCRIPT_DIR/developer_key.pem" 4096 2>/dev/null
        openssl pkcs8 -topk8 -inform PEM -outform DER -in "$SCRIPT_DIR/developer_key.pem" -out "$KEY_FILE" -nocrypt 2>/dev/null
        rm -f "$SCRIPT_DIR/developer_key.pem"
        echo "✅ Wygenerowano klucz deweloperski: $KEY_FILE"
    fi
else
    echo "✅ Użyto klucza deweloperskiego: $KEY_FILE"
fi

# 3. Przygotowanie folderu wyjściowego
mkdir -p "$SCRIPT_DIR/bin"
OUTPUT_PRG="$SCRIPT_DIR/bin/NaviApp.prg"

echo "⚙️  Kompilowanie aplikacji dla Garmin vívoactive 4..."
"$MONKEYC" \
    -f "$SCRIPT_DIR/monkey.jungle" \
    -o "$OUTPUT_PRG" \
    -d vivoactive4 \
    -y "$KEY_FILE" \
    -w

BIN_APP_DIR="$SCRIPT_DIR/../bin_app"
mkdir -p "$BIN_APP_DIR"
cp "$OUTPUT_PRG" "$BIN_APP_DIR/NaviApp.prg"

echo ""
echo "🎉 SUKCES! Pliki zostały wygenerowane:"
echo "👉 $OUTPUT_PRG"
echo "👉 $BIN_APP_DIR/NaviApp.prg"
echo ""
echo "🚀 Jak wgrać na zegarek Garmin vívoactive 4:"
echo " 1. Podłącz zegarek kablem USB do komputera (pojawi się jako dysk GARMIN)."
echo " 2. Skopiuj plik NaviApp.prg do folderu: GARMIN/APPS/"
echo " 3. Odłącz zegarek i uruchom aplikację z menu zegarka."
echo "====================================================="

