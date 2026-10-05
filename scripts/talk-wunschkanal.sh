#!/usr/bin/env bash
#
# Richtet auf einer Nextcloud den Wunschkanal für SideTube ein (ADR 0005, Weg 1) und gibt den
# Einrichtungscode für die App aus – als QR-Code (mit qrencode) und als Text.
#
#   OCC="<Aufruf von occ>" scripts/talk-wunschkanal.sh --server https://wolke.example.org --eltern anna[,ben]
#
#   OCC       wie occ aufgerufen wird, z. B. OCC="sudo -u www-data php /var/www/nextcloud/occ"
#   OCC_SSH   optional: Host, auf dem OCC läuft (Argumente werden dafür sicher maskiert), z. B.
#             OCC_SSH=server OCC="sudo -n docker exec -u www-data nextcloud php occ"
#
# Ohne --gespraech: legt Bot „SideTube" (nur Senden) und das Gespräch „SideTube-Wünsche" an, lädt die
# Eltern ein und schaltet den Bot dort ein. Den Schlüssel erzeugt Talk selbst; er steht in keiner
# Befehlszeile und in keiner Datei.
#
# Mit --gespraech TOKEN --schluessel-stdin: vorhandenes Gespräch und vorhandenen Bot weiterverwenden;
# der Schlüssel kommt über die Standardeingabe.
#
# Am Ende geht eine Testnachricht ins Gespräch. Die Ausgabe enthält den Schlüssel: nicht speichern,
# nicht weitergeben, nur in SideTube einlesen.
set -euo pipefail

server="" eltern="" gespraech="" schluessel_stdin=0 name="SideTube-Wünsche" bot="SideTube"
while (( $# )); do
  case "$1" in
    --server) server="${2%/}"; shift 2 ;;
    --eltern) eltern="$2"; shift 2 ;;
    --gespraech) gespraech="$2"; shift 2 ;;
    --schluessel-stdin) schluessel_stdin=1; shift ;;
    --name) name="$2"; shift 2 ;;
    --bot) bot="$2"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unbekannt: $1" >&2; exit 2 ;;
  esac
done
fehler() { echo "$*" >&2; exit 1; }
[[ -n "${OCC:-}" ]] || fehler "OCC ist nicht gesetzt (siehe --help)."
[[ "$server" == https://* ]] || fehler "--server muss mit https:// beginnen."
[[ "$eltern" =~ ^[A-Za-z0-9._@-]+(,[A-Za-z0-9._@-]+)*$ ]] || fehler "--eltern: Nutzernamen, mit Komma getrennt."
command -v python3 >/dev/null || fehler "python3 fehlt."
# Schlüssel zuerst lesen: Später aufgerufene Programme (ssh) dürfen die Eingabe nicht verbrauchen.
schluessel=""
if (( schluessel_stdin )); then IFS= read -r schluessel || fehler "Kein Schlüssel auf der Standardeingabe."; fi
occ() {
  if [[ -n "${OCC_SSH:-}" ]]; then ssh -n "$OCC_SSH" "$OCC $(printf '%q ' "$@")"; else $OCC "$@"; fi
}

occ app:list 2>/dev/null | awk '/^Enabled/{e=1} /^Disabled/{e=0} e && /- spreed:/{f=1} END{exit !f}' \
  || fehler "Nextcloud Talk (spreed) ist nicht eingeschaltet: occ app:enable spreed"

IFS=, read -r -a liste <<< "$eltern"
if [[ -z "$gespraech" ]]; then
  # Ausgabe von talk:bot:create (Talk 22): „Bot installed", „ID: <n>", „Secret: <schlüssel>".
  ausgabe="$(occ talk:bot:create --no-setup "$bot" "Meldet neue Wünsche aus SideTube (nur Senden)")"
  bot_id="$(sed -n 's/^ID: //p' <<< "$ausgabe")"
  schluessel="$(sed -n 's/^Secret: //p' <<< "$ausgabe")"
  unset ausgabe
  [[ "$bot_id" =~ ^[0-9]+$ && -n "$schluessel" ]] || fehler "Bot konnte nicht angelegt werden (Name schon vergeben?)."
  args=(--owner="${liste[0]}")
  for n in "${liste[@]}"; do args+=(--user="$n"); done
  gespraech="$(occ talk:room:create "${args[@]}" --description="Hier meldet SideTube neue Wünsche der Kinder." "$name" \
    | sed -n 's/^Room token: //p')"
  [[ -n "$gespraech" ]] || fehler "Gespräch konnte nicht angelegt werden."
  occ talk:bot:setup "$bot_id" "$gespraech" >/dev/null
else
  (( schluessel_stdin )) || fehler "Mit --gespraech den Schlüssel über --schluessel-stdin übergeben."
  occ talk:bot:list --output=json "$gespraech" | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin) else 1)' \
    || fehler "Im Gespräch $gespraech ist kein Bot eingeschaltet."
fi
(( ${#schluessel} >= 40 )) || fehler "Schlüssel zu kurz."

# Testnachricht wie die App: HMAC-SHA256(Schlüssel, Zufall + Text), hex. Schlüssel nur über die Umgebung.
erwaehnung="$(printf '@%s ' "${liste[@]}")"
SERVER="$server" GESPRAECH="$gespraech" SCHLUESSEL="$schluessel" TEXT="${erwaehnung}SideTube: Wunschkanal eingerichtet." \
python3 - <<'PY' || fehler "Testnachricht abgelehnt – Schlüssel, Gespräch oder Bot prüfen."
import hashlib, hmac, json, os, secrets, sys, urllib.error, urllib.request
zufall = secrets.token_hex(32)
text = os.environ["TEXT"]
signatur = hmac.new(os.environ["SCHLUESSEL"].encode(), (zufall + text).encode(), hashlib.sha256).hexdigest()
anfrage = urllib.request.Request(
    f'{os.environ["SERVER"]}/ocs/v2.php/apps/spreed/api/v1/bot/{os.environ["GESPRAECH"]}/message',
    data=json.dumps({"message": text}).encode(), method="POST",
    headers={"Content-Type": "application/json", "Accept": "application/json", "OCS-APIRequest": "true",
             "X-Nextcloud-Talk-Bot-Random": zufall, "X-Nextcloud-Talk-Bot-Signature": signatur})
try:
    with urllib.request.urlopen(anfrage, timeout=20) as antwort:
        sys.exit(0 if antwort.status == 201 else 1)
except urllib.error.HTTPError as e:
    print(f"HTTP {e.code}", file=sys.stderr); sys.exit(1)
PY

code="$(SERVER="$server" GESPRAECH="$gespraech" SCHLUESSEL="$schluessel" ELTERN="$eltern" python3 -c '
import json, os
print(json.dumps({"v": 1, "art": "talk", "server": os.environ["SERVER"], "gespraech": os.environ["GESPRAECH"],
                  "schluessel": os.environ["SCHLUESSEL"], "erwaehnen": os.environ["ELTERN"].split(",")},
                 separators=(",", ":"), ensure_ascii=False))')"
unset schluessel
echo "Testnachricht ist im Gespräch „${name}“ (${gespraech}) angekommen."
echo "Einrichtungscode für SideTube → Einstellungen → Eltern benachrichtigen (enthält den Schlüssel, nicht speichern):"
if command -v qrencode >/dev/null; then qrencode -t ANSIUTF8 -m 2 "$code"; else echo "(qrencode fehlt – nur Text)"; fi
echo "$code"
