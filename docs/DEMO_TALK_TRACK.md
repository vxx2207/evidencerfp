# Demo Talk Track

Zielzeit: **4 Minuten**. Alle Unternehmen, Personen, Dokumente, Kennzahlen und Resultate in der Demo sind synthetisch. Für einen Recruiter-Schnellscan startet der Button **„60-Sekunden-Demo starten“** dieselben Stationen in komprimierter Form. Jede Station besitzt zusätzlich einen teilbaren Hash-Link.

## 0:00–0:30 · Problem und Ziel

> „Ein Security-Fragebogen kommt oft kurz vor der Kaufentscheidung. Presales sucht dieselben Nachweise erneut, Security wird zum Engpass und Copy-and-paste kann falsche Zusagen erzeugen. EvidenceRFP erstellt deshalb keine freie KI-Antwort, sondern einen überprüfbaren Entwurf mit Quellen und kontrollierter Freigabe.“

Zeigen: Übersicht mit 24 Fragen, den drei Statuswerten und der klar als synthetisch gekennzeichneten Business-Impact-Karte. Danach „60-Sekunden-Demo starten“ wählen.

## 0:30–1:10 · Import und Verarbeitung

> „Ich verwende einen synthetischen Fragebogen der Nordstern Bank, fünf fiktive CertaAccess-Dokumente und einen künstlichen IAM-Snapshot. In der öffentlichen Demo sind die Ergebnisse vorbereitet. Upload und lokale KI gibt es nur im lokalen Modus.“

Zeigen: Datenquellen, Versionsdatum und deutlich sichtbares Demo-Badge. Kurz den Ablauf Import → Retrieval → IAM-Regeln → Review erklären.

## 1:10–1:55 · Belegte Antwort

Tour-Station `#tour/source`: Frage **IAM-001** zur phishing-resistenten MFA öffnen.

> „Der wichtigste Klick ist nicht die Antwort, sondern der Beleg. Die Zitation enthält Dokument, Abschnitt und Textstelle. Der Server akzeptiert sie nur, wenn der Chunk tatsächlich abgerufen wurde und das Zitat darin vorkommt.“

Zeigen: Zitation „2.1 Starke Authentifizierung“ anklicken; die vollständige Quellpassage wird geöffnet und der wörtliche Beleg hervorgehoben.

## 1:55–2:35 · Konflikt und Nichtwissen

Tour-Station `#tour/conflict`: Frage **IAM-020** zur Aufbewahrungsfrist öffnen. Das aktuelle Compliance-Konzept nennt 365 Tage, der ältere Servicekatalog 180 Tage.

> „Hier behauptet die Anwendung nicht, sie kenne die Wahrheit. Sie zeigt beide Aussagen, markiert die ältere Quelle und sperrt die Freigabe. Ein Product- oder Security-Owner muss entscheiden.“

Danach über `#tour/not-found` die Frage **IAM-022** zum BSI-C5-Typ-2-Bericht öffnen.

> „Fehlt die Evidenz, entsteht keine Halluzination. EvidenceRFP formuliert eine interne Klärungsfrage: Wer kann diese Aussage autorisieren oder welches Dokument fehlt?“

## 2:35–3:20 · IAM-Kontrollen

> „Produktdokumente zeigen den Soll-Zustand. Der Snapshot zeigt einen synthetischen Ist-Zustand. Drei deterministische Regeln finden hier ein privilegiertes Konto ohne phishing-resistente MFA, ein verwaistes privilegiertes Konto und ein zu altes Service-Account-Secret.“

Tour-Station `#tour/finding`: Finding **finding-001 / IAM-MFA-01** öffnen. Regel-ID, betroffenes Objekt, Evidenz und Handlungsempfehlung zeigen. Betonen, dass kein LLM die Regelentscheidung trifft.

## 3:20–3:45 · Review und Export

> „Der Sales Engineer editiert und entscheidet. Konflikte und fehlende Nachweise bleiben als bewusste Klärung dokumentiert. Der Export enthält nicht nur Antworten, sondern Status, Quellen, Review-Protokoll und Findings – ein Evidence Pack statt einer Blackbox.“

Tour-Station `#tour/export`: die einzige offene Frage **IAM-004** freigeben. Zeigen, wie die letzte Entscheidung den XLSX-Export aktiviert; anschließend das Evidence Pack mit den vier Blättern `Antworten`, `IAM Findings`, `Quellen` und `Review` herunterladen.

## 3:45–4:00 · Geschäftswert und Abschluss

> „Der Nutzen ist schnellere Recherche mit weniger unbelegten Zusagen. Das ROI-Modell legt jede Annahme offen; ein PoC misst Zeitersparnis und Qualität gemeinsam. Das Projekt verbindet Business Discovery, sicheren KI-Einsatz, IAM und eine verkaufsfähige Demo.“

## Falls etwas nicht funktioniert

- Nicht improvisiert echte Uploads verwenden; auf die statische Demo wechseln.
- Bei langsamem lokalen Modell ein vorbereitetes Ergebnis öffnen und transparent benennen.
- Keine Performance- oder ROI-Zahl als Kundenfakt darstellen; immer „synthetische Annahme“ sagen.
- Die Demo nie mit einer Freigabe eines Konfliktfalls beenden; stattdessen die kontrollierte Sperre als Stärke zeigen.
- Vor einer neuen Vorführung „Demo zurücksetzen“ verwenden, damit IAM-004 wieder die einzige offene Entscheidung ist.
