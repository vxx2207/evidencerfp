# ROI Case

Alle Werte auf dieser Seite sind **synthetische Beispielannahmen**, keine gemessenen Kundenergebnisse. Das Rechenmodell soll in einer Discovery mit echten Kundenzahlen ersetzt werden.

## Eingaben

| Variable | Beispielwert | Bedeutung |
|---|---:|---|
| `R` | 8 | RFPs pro Monat |
| `Q` | 75 | Fragen pro RFP |
| `t_alt` | 8 min | durchschnittliche aktive Zeit pro Frage heute |
| `t_neu` | 3 min | aktive Zeit pro Frage mit EvidenceRFP |
| `p_nutzbar` | 70 % | Anteil der Fragen mit nutzbarem Erstentwurf |
| `C` | 75 € | interner Vollkostensatz pro Stunde |
| `K_monat` | 900 € | angenommene monatliche Betriebs-/Lizenzkosten |
| `I` | 12.000 € | einmalige Einführungskosten |

## Formeln

Nur der nutzbare Anteil wird als Zeiteinsparung gerechnet. Reviewzeit ist bereits in `t_neu` enthalten.

```text
Fragen_pro_Monat = R × Q
Gesparte_Minuten = R × Q × p_nutzbar × (t_alt − t_neu)
Gesparte_Stunden = Gesparte_Minuten ÷ 60
Monatlicher_Bruttonutzen = Gesparte_Stunden × C
Monatlicher_Nettonutzen = Monatlicher_Bruttonutzen − K_monat
Amortisationszeit_Monate = I ÷ Monatlicher_Nettonutzen
Jahres_ROI = ((12 × Monatlicher_Bruttonutzen) − (I + 12 × K_monat)) ÷ (I + 12 × K_monat)
```

## Beispielrechnung

```text
Fragen_pro_Monat = 8 × 75 = 600
Gesparte_Minuten = 8 × 75 × 0,70 × (8 − 3) = 2.100
Gesparte_Stunden = 2.100 ÷ 60 = 35
Monatlicher_Bruttonutzen = 35 × 75 € = 2.625 €
Monatlicher_Nettonutzen = 2.625 € − 900 € = 1.725 €
Amortisationszeit = 12.000 € ÷ 1.725 € = 6,96 Monate
Jahres_ROI = ((12 × 2.625 €) − (12.000 € + 12 × 900 €)) ÷ (12.000 € + 12 × 900 €)
            = 8.700 € ÷ 22.800 € = 38,2 %
```

## Sensitivität

| Szenario | Nutzbarer Anteil | Zeitersparnis je nutzbarer Frage | Bruttonutzen/Monat | Netto/Monat |
|---|---:|---:|---:|---:|
| konservativ | 50 % | 3 min | 1.125 € | 225 € |
| Basis | 70 % | 5 min | 2.625 € | 1.725 € |
| ambitioniert | 80 % | 6 min | 3.600 € | 2.700 € |

Die Rechnung monetarisiert bewusst **nicht** die möglicherweise kürzere Deal-Zeit, vermiedene Falschaussagen oder weniger Unterbrechungen im Security-Team. Diese Effekte sollten getrennt gemessen werden, um Doppelzählung zu vermeiden.

## Messplan

- Zwei historische RFPs ohne Tool als Baseline messen.
- Aktive Bearbeitungszeit pro Frage, Anzahl Reviews und Nachfragen protokollieren.
- Im PoC dieselben Dokumente und Fragen verwenden.
- Nur akzeptierte Erstentwürfe als nutzbar zählen.
- Qualitätsmetriken stets gemeinsam mit der Zeitersparnis berichten.
