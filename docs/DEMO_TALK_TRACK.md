# Demo Talk Track

Zielzeit: **4 Minuten**. Alle Unternehmen, Personen, Dokumente, Kennzahlen und Resultate in der Demo sind synthetisch.

## 0:00–0:30 · Problem und Ziel

> „Ein Security-Fragebogen kommt oft kurz vor der Kaufentscheidung. Presales sucht dieselben Nachweise erneut, Security wird zum Engpass und Copy-and-paste kann falsche Zusagen erzeugen. EvidenceRFP erstellt deshalb keine freie KI-Antwort, sondern einen überprüfbaren Entwurf mit Quellen und kontrollierter Freigabe.“

Zeigen: Übersicht mit 24 Fragen und den drei Statuswerten.

## 0:30–1:10 · Import und Verarbeitung

> „Ich verwende einen synthetischen Fragebogen der Nordstern Bank, fünf fiktive CertaAccess-Dokumente und einen künstlichen IAM-Snapshot. In der öffentlichen Demo sind die Ergebnisse vorbereitet. Upload und lokale KI gibt es nur im lokalen Modus.“

Zeigen: Datenquellen, Versionsdatum und deutlich sichtbares Demo-Badge. Kurz den Ablauf Import → Retrieval → IAM-Regeln → Review erklären.

## 1:10–1:55 · Belegte Antwort

Eine `SUPPORTED`-Frage öffnen, beispielsweise Verschlüsselung im Ruhezustand.

> „Der wichtigste Klick ist nicht die Antwort, sondern der Beleg. Die Zitation enthält Dokument, Abschnitt und Textstelle. Der Server akzeptiert sie nur, wenn der Chunk tatsächlich abgerufen wurde und das Zitat darin vorkommt.“

Zeigen: Zitation anklicken; Quellpassage wird hervorgehoben.

## 1:55–2:35 · Konflikt und Nichtwissen

Eine MFA-Frage mit widersprüchlicher älterer und aktueller Aussage öffnen.

> „Hier behauptet die Anwendung nicht, sie kenne die Wahrheit. Sie zeigt beide Aussagen, markiert die ältere Quelle und sperrt die Freigabe. Ein Product- oder Security-Owner muss entscheiden.“

Danach eine `NOT_FOUND`-Frage öffnen.

> „Fehlt die Evidenz, entsteht keine Halluzination. EvidenceRFP formuliert eine interne Klärungsfrage: Wer kann diese Aussage autorisieren oder welches Dokument fehlt?“

## 2:35–3:20 · IAM-Kontrollen

> „Produktdokumente zeigen den Soll-Zustand. Der Snapshot zeigt einen synthetischen Ist-Zustand. Drei deterministische Regeln finden hier ein privilegiertes Konto ohne phishing-resistente MFA, ein verwaistes privilegiertes Konto und ein zu altes Service-Account-Secret.“

Zeigen: Finding mit Regel-ID, betroffenem Objekt, Evidenz und Handlungsempfehlung. Betonen, dass kein LLM die Regelentscheidung trifft.

## 3:20–3:45 · Review und Export

> „Der Sales Engineer editiert und genehmigt. Konflikte bleiben gesperrt. Der Export enthält nicht nur Antworten, sondern Status, Quellen und offene Findings – ein Evidence Pack statt einer Blackbox.“

Zeigen: Freigabe und XLSX-Exportvorschau.

## 3:45–4:00 · Geschäftswert und Abschluss

> „Der Nutzen ist schnellere Recherche mit weniger unbelegten Zusagen. Das ROI-Modell legt jede Annahme offen; ein PoC misst Zeitersparnis und Qualität gemeinsam. Das Projekt verbindet Business Discovery, sicheren KI-Einsatz, IAM und eine verkaufsfähige Demo.“

## Falls etwas nicht funktioniert

- Nicht improvisiert echte Uploads verwenden; auf die statische Demo wechseln.
- Bei langsamem lokalen Modell ein vorbereitetes Ergebnis öffnen und transparent benennen.
- Keine Performance- oder ROI-Zahl als Kundenfakt darstellen; immer „synthetische Annahme“ sagen.
- Die Demo nie mit einer Freigabe eines Konfliktfalls beenden; stattdessen die kontrollierte Sperre als Stärke zeigen.
