# Control Center design rework (J25.5)

User decisions (2026-11-12/18): the Control Center takes over the design language of the web app (grouped navigation,
overview pages, search, favourites, side panels) instead of a 1:1 port; **all dialogs** are part of the rework and follow
Grafana's design recommendations (Saga design system and Grafana's UX writing guide). Brand: Blueberry `#1a2a45`, Sun
`#ffde00`, Montserrat; a light and a dark variant.

## Stages

1. **Theme**: AtlantaFX (MIT) Primer light/dark as base stylesheet, the brand colours as its colour tokens, Montserrat;
   `Styles.css` reduced to JEVis-specific rules on the AtlantaFX tokens. The theme switch keeps light, dark (and the
   Bridge theme as long as it is used).
2. **Navigation**: grouped, collapsible sidebar instead of the vertical plugin tabs (Start, Dashboards, Analysen,
   ISO 50001, Betrieb, Anlagen, Finanzen, Berichte, Konfiguration); icon-only mode; plugins unchanged.
3. **Top bar**: site picker, Strg+K search over modules, dashboards, analyses; favourites and recently opened (user
   preferences).
4. **Overview pages**: dashboards and analyses start with search, site filter, favourites, recently opened.
5. **Dialogs and editors** (below), step by step per plugin.

Status 2026-11-18: stage 1 (AtlantaFX Primer light/dark under the brand tokens, Modena fallbacks for ControlsFX) and
stage 2 (`application/navigation/PluginSidebar`, groups and icon-only mode as user preferences; the plugin tab pane
only switches the content) are built; stages 3–5 open.

## Dialog rules (Grafana Saga / UX writing, adapted to a desktop app)

Sources: Saga [Modal](https://grafana.com/developers/saga/components/modal),
[Forms](https://grafana.com/developers/saga/patterns/forms/), [Save](https://grafana.com/developers/saga/patterns/save/),
[Button](https://grafana.com/developers/saga/components/buttons/button), Grafana
[UX writing](https://grafana.com/docs/writers-toolkit/write/style-guide/ux-writing/).

- **Which container**
  - *Modal dialog* only for an immediate decision or a short form (≤ 5 fields) and for confirmations when an error is
    costly; not for information that is not essential and not for tasks done repeatedly.
  - *Side panel* (drawer from the right, inside the main window) for editors with more than 5 fields (attribute and
    object editors, widget settings, analysis/data row settings, ISO items); the content behind stays visible.
  - *Inline* editing for rows in tables (with an explicit save of the row).
  - *Own window* only for real work areas (e.g. a detached dashboard), not for settings.
- **Structure**: header (title, optional one-line description), body (sections, „Erweiterte Optionen“ collapsed), action
  bar at the bottom. Sizes S (confirm, ~420 px), M (short form, ~640 px), L (lists/tables, ~960 px); scrollable body,
  footer always visible. Escape and the close icon dismiss (with the unsaved-changes check).
- **Buttons**: exactly one primary button (Sun) per dialog, others secondary; destructive actions red and never as a
  second solid button next to the primary; platform order through `ButtonBar` (Windows: primary left of Abbrechen, both
  right); no mix of icon and text buttons in one group.
- **Labels**: start with a verb, one or two words („Speichern“, „Löschen“, „Analyse öffnen“), specific instead of
  OK/Ja („Löschen“ / „Abbrechen“ instead of OK / Abbrechen), sentence case, no „Bitte“.
- **Confirmations**: title as a question naming the action and the object („Analyse „Strom 2026“ löschen?“); body says
  the consequence and the scope („Die Analyse kommt in den Papierkorb.“ / „Betrifft 75 Benutzer.“); no „Sind Sie
  sicher?“.
- **Saving**: the save button is always visible, enabled after a change; unsaved changes are kept on navigation only
  after asking („Änderungen verwerfen?“); success and errors as a short notification (toast) in the main window, not as a
  modal message; auto-save only for low-impact settings.
- **Forms**: labels above the fields, left-aligned; only mark required fields that really are (`*`); inline validation
  at the field with how to fix it („Wert muss eine Zahl sein“, not „ungültig“); the save button is not disabled because
  of errors – saving shows the errors.
- **Messages**: error = what went wrong + what to do; no apologies; tooltips under 120 characters.

## Building blocks (JEConfig `application/ui`)

- `SucDialog` (base of the modal dialogs: header, body, action bar, sizes, Escape, dirty check), `Confirm`
  (confirmations with object and consequence), `Notify` (toasts instead of information alerts), `SidePanel` (drawer of
  the main window for editors). New code uses only these; existing dialogs move over plugin by plugin
  (inventory 2026-11-18: 34 dialog classes, 52 dialog instances in 43 files, 172 `Alert`s in 81 files, 34 own windows).
