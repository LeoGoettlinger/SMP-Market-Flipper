# Entwicklerfunktion: lokale Order/AH-Aufzeichnung

> **Kurz gesagt:** Diese Funktion zeichnet auf Wunsch lokal auf, welche verwalteten Minecraft-GUI-Bildschirme du öffnest und welche Slots du anklickst. Sie ist ein Diagnosewerkzeug für die Entwicklung des Order→AH-Adapters. Sie ist **kein Bot**, macht keine Klicks für dich und schaltet die Order-Automatisierung nicht frei.
>
> Dieser Leitfaden ist für dich als Nutzer geschrieben. Für Architektur, Grenzen und Datenschutz siehe zusätzlich [Local action capture](LOCAL_ACTION_CAPTURE.md), [Order→AH](ORDER_TO_AH.md) und [Agent.md](../Agent.md).

## 1. Was die Bilder bereits zeigen

Die fünf zuletzt zugesandten Bilder helfen, die sichtbare Oberfläche und die Reihenfolge der Ansichten festzuhalten:

- Die Order-Liste heißt `ORDERS`; sichtbar ist eine Order für `Tropenbaumstamm`.
- Im Menü `Deine Orders` sind die Filter `Alle Orders`, `Offene Orders`, `Abgeschlossene Orders`, `Gelöste Orders` und `Stornierte Orders` zu sehen.
- Der angezeigte Order-Tooltip nennt `$202` pro Stück, `2.048/2.048` geliefert und `$413.696/$413.696` ausgezahlt. Außerdem zeigt er `Items abholen: 1.724`.
- Im selben Tooltip stehen sowohl `Nächster Verfall: 4d 2h` als auch `1.724x verfällt spätestens in 4d 20h`. Diese beiden Angaben sind nicht gleich und dürfen nicht zu einer einzigen Zeitangabe zusammengezogen werden.
- Die Ansichten zeigen außerdem einen Order-Bearbeiten-Bildschirm sowie einen Button mit dem Tooltip `Items abholen`.

Das reicht, um sichtbare Beschriftungen und mögliche Zustände zu dokumentieren. Es reicht **nicht**, um die genaue Order-ID, die tatsächlichen internen Item-Daten, die Bedeutung jeder Slot-Aktion oder die Sicherheit eines Abholvorgangs festzustellen. Ein statisches Bild beweist auch nicht, dass die GUI bei jedem Zustand gleich aussieht. Solange diese Punkte nicht mit einem erlaubten privaten Testprofil geprüft sind, bleibt Order→AH im Mod fail-closed.

**Bitte klicke nicht auf „Items abholen“, nur um Daten zu erzeugen.** Die angezeigte Order ist zwar als `2.048/2.048` geliefert markiert, aber der Tooltip weist nur `1.724` aktuell abholbare Items aus. Der Unterschied muss verstanden und mit einer vollständigen, erlaubten Test-Order nachvollzogen werden, bevor irgendein automatisches Einsammeln implementiert werden darf. Keine Teilmenge abholen oder verkaufen.

## 2. Was der Recorder genau macht

Wenn du ihn einschaltest, legt SC-Market eine lokale UTF-8-Textdatei im **JSONL-Format** an. JSONL bedeutet: ein JSON-Objekt pro Zeile.

Aufgezeichnet werden — soweit die jeweilige Ansicht es bereitstellt — Zeitstempel in UTC, Ereignistyp, Fenstertitel, Container-ID, angeklickter Slot, Item-Registry-ID und Stack-Anzahl sowie Maustaste und Bildschirmkoordinaten. Es werden ausschließlich GUI-Klicks erfasst; Aktionen in der Spielwelt, gehaltene Items und Zielobjekte werden nicht aufgezeichnet.

**Nicht aufgezeichnet** werden Chatnachrichten, getippter Text, Tastatureingaben, Zugangsdaten, Spielername, Weltkoordinaten, Netzwerkpakete, Hover-Bewegungen, Tooltip-/Lore-Text oder das Ergebnis einer Transaktion. Der Recorder macht auch keine Screenshots. Deshalb sind die JSONL-Datei **und** passende Screenshots/Notizen zusammen nützlicher als die Datei allein.

Die Aufzeichnung ist standardmäßig ausgeschaltet und wird nur lokal gespeichert. Sie wird nicht automatisch hochgeladen und nicht in das Git-Repository übernommen. Das Repository und der GitHub-Actions-JAR-Download sind nicht der Speicherort deiner Aufnahme.

## 3. Vorbereitung am PC

1. Verwende den aktuellen SC-Market-JAR aus dem erfolgreichen GitHub-Actions-Lauf. Installiere ihn nur in einer Minecraft-Installation mit der dazu passenden Version und Fabric-Umgebung. Die Download-Schritte stehen in der [README](../README.md).
2. Nutze ausschließlich einen Server, den du selbst betreibst oder dessen Betreiber diese Tests ausdrücklich erlaubt hat. Am besten verwendest du ein Wegwerf-/Testprofil mit Testdaten.
3. Warte mit der Aufnahme, bis du wieder am PC bist und in Ruhe prüfen kannst, welcher Minecraft-Spielordner tatsächlich verwendet wird.
4. Stelle sicher, dass du keine Serveradresse, Spielernamen, privaten Chat, API-Schlüssel, Zugangsdaten oder vertrauliche Konfiguration in Screenshots oder Begleittext mitschickst.
5. Die Mod-Einstellung für die lokale Aufzeichnung bleibt gespeichert. Wenn sie beim letzten Spielen eingeschaltet blieb, kann beim nächsten Start automatisch eine neue Aufnahme beginnen. Prüfe den Status in den Order→AH-Einstellungen und schalte die Aufnahme aus, falls du gerade nicht aufnehmen möchtest.

## 4. Aufnahme starten und die relevanten Ansichten durchgehen

1. Verbinde dich mit dem erlaubten privaten Testserver. **Schalte die Live-Order-Automatisierung nicht ein.** Der Recorder benötigt sie nicht.
2. Öffne SC-Market mit **Rechts-Shift**.
3. Öffne den Tab **Order→AH**.
4. Suche im Einstellungsbereich den Schalter **LOCAL CAPTURE · OFF**. Die Schaltfläche **CAP** daneben legt die Obergrenze der aufgezeichneten Aktionen fest. Standardmäßig sind es 5.000 Aktionen; die UI bietet 100, 500, 1.000, 2.500, 5.000 und 10.000 an. Für einen kurzen Durchlauf reichen normalerweise 1.000 oder 2.500.
5. Klicke auf **LOCAL CAPTURE · OFF**. Der Status sollte zu **LOCAL CAPTURE · ON** wechseln und die Mitteilung den lokalen Dateinamen nennen. Wenn stattdessen `CAPTURE STOPPED · TOGGLE OFF` erscheint, läuft gerade keine Aufnahme; schalte den Schalter aus und wieder ein.
6. Navigiere die relevanten Ansichten **manuell und langsam**. Notiere dir die Reihenfolge mit kurzen Nummern, zum Beispiel:
   - `1` — `ORDERS`-Liste, bevor ein Filter geöffnet wird;
   - `2` — Statusfilter `Deine Orders` geöffnet;
   - `3` — Order-Liste mit der betreffenden Order;
   - `4` — Tooltip der Order sichtbar (Tooltip separat als Screenshot festhalten; Hover wird nicht in JSONL aufgezeichnet);
   - `5` — Order-Bearbeiten-Ansicht;
   - `6` — AH-Liste/Angebot, falls die entsprechende Ansicht sicher ohne Kauf- oder Verkaufsaktion geöffnet werden kann.
7. Klicke nur auf ungefährliche Navigations-/Filterelemente. **Nicht** abholen, kaufen, erstellen, bestätigen, listen oder stornieren. Wenn ein Bildschirm eine Transaktion oder eine folgenreiche Bestätigung verlangt, dort anhalten und nur den sichtbaren Zustand als Screenshot sichern. Keine Teil-Order oder Teilmenge einsammeln.
8. Mache Screenshots, auf denen der vollständige Fenstertitel, die betreffende Order-Zeile und der ganze Tooltip zu sehen sind. Halte den Mauszeiger über der relevanten Zeile bzw. dem Button, damit der Tooltip sichtbar ist. Das ist nötig, weil der Recorder Tooltips nicht lesen kann.
9. Wenn du fertig bist, gehe zurück zu den Order→AH-Einstellungen und klicke **LOCAL CAPTURE · ON**, damit es wieder **LOCAL CAPTURE · OFF** anzeigt. Dadurch wird die Datei geschlossen und gepuffert auf die Festplatte geschrieben.

### Wichtig zum CAP-Limit

Die eingestellte Zahl begrenzt die Anzahl der Ereignisdatensätze, nicht die Zahl der Minecraft-Minuten. Bei Erreichen des Limits stoppt der Recorder selbst. Der Schalter kann dann noch aktiviert aussehen; die Anzeige `CAPTURE STOPPED · TOGGLE OFF` signalisiert, dass keine Ereignisse mehr geschrieben werden. Schalte in diesem Fall aus und nur dann wieder ein, wenn du eine neue Datei beginnen möchtest. Die Dateigröße ist zusätzlich auf 4 MiB begrenzt. Bei einem Datenträgerfehler stoppt die Aufzeichnung ebenfalls, ohne die Spielaktion zu blockieren.

## 5. Wo die Datei liegt

Dateinamen sehen ungefähr so aus: `order-capture-20261022-152310.jsonl`. Der Zeitanteil wird in UTC gebildet. Falls es im selben Moment bereits eine Datei gibt, wird eine Nummer angehängt, zum Beispiel `-1`.

Der Pfad ist relativ zum tatsächlichen Minecraft-Spielordner:

```text
.minecraft/config/sc-market/diagnostics/order-capture-*.jsonl
```

Übliche Speicherorte:

- **Windows:** `%APPDATA%\.minecraft\config\sc-market\diagnostics\`
- **macOS:** `~/Library/Application Support/minecraft/config/sc-market/diagnostics/`
- **Linux:** `~/.minecraft/config/sc-market/diagnostics/`

Wenn du im Launcher ein eigenes Spielverzeichnis eingestellt hast, suche dort unter `config/sc-market/diagnostics/`. Die Mod-Einstellungen stehen separat unter `config/sc-market/order-module.properties`; du brauchst diese Datei für die Analyse nicht mitzuschicken.

## 6. Was du mir später schicken solltest

Bitte hänge die `.jsonl`-Datei **als Datei** an den Chat an, nicht nur den Textauszug. Dazu reichen zunächst:

- die Screenshots der relevanten Screens/Tooltips ohne Serveradresse und Spielernamen;
- deine nummerierte Kurzbeschreibung der Aufnahme, zum Beispiel `1: Order-Liste geöffnet, 2: Filtermenü, 3: Tooltip angesehen`;
- Minecraft-Version, SC-Market-JAR-Version oder den zugehörigen Commit-SHA;
- Betriebssystem/Launcher, GUI-Skalierung und Auflösung;
- eine kurze Notiz, welche Angaben du nur sehen konntest und welche Aktion du bewusst **nicht** ausgeführt hast.

Falls die betreffende Order-ID auf dem Bildschirm oder im Tooltip nicht sichtbar ist, schreibe einfach `nicht sichtbar`. Bitte nicht raten. Teile niemals API-Schlüssel, Passwörter, private Serverkonfiguration, vollständige Serveradresse oder andere Zugangsdaten. Prüfe die Screenshots vor dem Senden auf private Informationen. Die JSONL-Datei enthält selbst keine Chat- oder Tastenanschläge, aber ein eigener Server kann in Fenstertiteln oder Item-IDs dennoch identifizierbare Angaben zeigen.

## 7. Was danach mit der Aufnahme passiert

Die Aufnahme wird zunächst lokal analysiert, um die echten Eventfolgen, Screen-Titel, Slot-IDs und Item-IDs zu verstehen. Danach können wir einen privaten, eng begrenzten Screen-/Order-Parser und automatisierte Tests für erkannte Zustände ergänzen. Unklare, widersprüchliche oder fehlende Werte müssen zu einem sicheren Abbruch führen.

Erst wenn der Parser Order-ID, Item, Preis, Gesamt-/Rest-/Liefermenge, Status, Abholbarkeit und Bestätigungssummen im erlaubten privaten Test reproduzierbar verifiziert und ein vollständiger End-to-End-Test bestanden ist, kann eine erste begrenzte Live-Funktion erwogen werden. Die Server-Allowlist bleibt der Autorisierungscheck; ein privates Serverprofil und die Preis-, Inventar-, Mengen- und Transaktionsschutzprüfungen werden dadurch nicht ersetzt. Die Aufnahme selbst schaltet nichts davon frei.
