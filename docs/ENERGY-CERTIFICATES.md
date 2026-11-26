# Energy certificates: store, overview, reminders and transfer

Existing energy certificates (Energieausweise, Verbrauchs- or Bedarfsausweis) are kept with the buildings they belong
to, listed in one overview (values, deadlines, issuer, responsible persons) and sent to third parties through several
interfaces, by hand or on a schedule.

**Out of scope:** creating certificates (calculation after GEG/DIN V 18599) and requesting registration numbers from
the DIBt. The data are entered from the certificate the issuer handed over.

## Classes (`var/classes/EnergyCertificatePlugin`, translations in `var/i18n/EnergyCertificatePlugin`)

- **Energy Certificate Directory** (unique per parent; parents Building and Organization; inherits Directory).
  The folder "Energieausweise" of a building, created by the web app or the Control Center when the first
  certificate is added.
- **Energy Certificate** (parent: the directory). Attributes:
  - `Data` (String, JSON of `org.jevis.commons.certificate.EnergyCertificate`). A new sample is written per save, and
    its timestamp is the `version` for the conflict check.
  - `File` (File): the certificate as PDF.
  - `Transfer State` (String, JSON, written only by JENotifier): reminders sent and transfers made, see below.
- **Energy Certificate Transfer** (parents Organization, Building and Energy Certificate Directory): a transfer
  target. All certificates below its parent are in its scope. Attributes:
  - `Enabled` (Boolean);
  - `Channel` (String): `MAIL`, `SFTP` or `HTTP`;
  - `Format` (String):
    - `CSV`: one line per certificate, `;`, UTF-8 with BOM;
    - `JSON`: array of certificates with building and file name;
    - `ZIP`: the CSV, the JSON and all PDFs;
  - `Schedule` (String): `MANUAL`, `DAILY`, `WEEKLY`, `MONTHLY` or `ON_CHANGE` (only certificates changed since the
    last transfer);
  - `Recipients` (String: user/group ids and e-mail addresses separated by `;`, e.g. `24497; amt@example.org`;
    channel `MAIL`);
  - `Host`, `Port`, `User`, `Password` (password type, never sent to the web app), `Path` (channel `SFTP`);
  - `URL` and `Token` (password type) (channel `HTTP`: POST of the export as request body with its content type and
    `Content-Disposition` file name, `Authorization: Bearer <token>` if set; 2xx = success);
  - `Requested` (String): set by "Jetzt übermitteln", read and cleared by JENotifier;
  - `Last Transfer` (String, JSON `{at, success, count, error, lastSuccess, hostKey}`; `hostKey` = SHA-256
    fingerprint of the SFTP server key accepted on first use).

## Logic (JECommons `org.jevis.commons.certificate`, tested in `CertificateLogicTest`)

- `CertificateLogic.validate`:
  - type required, valid-until a date after the issue date;
  - energy class one of A+…H;
  - numbers not negative;
  - reminder months 0–24.
- `defaultValidUntil(issued)`: 10 years minus one day (GEG §79). Prefilled in the form and can be changed.
- `status(c, today)`:
  - `EXPIRED` after valid-until;
  - `EXPIRING` from the largest reminder period (default 6 months) on;
  - `VALID` otherwise;
  - `UNKNOWN` without a date.
- `dueReminder(c, today, sent)`: the closest reached reminder not yet sent. If several are due at once (e.g. a missed
  6-month reminder), only one mail goes out.
- `CertificateExport`: CSV (`;`, UTF-8 with BOM, German headers and decimal comma), JSON (numbers as numbers) or ZIP
  (CSV, JSON and the PDFs as `pdf/<id>_<name>`); file name `Energieausweise_<yyyyMMdd-HHmm>.<ext>`.
- `TransferSchedule.isDue(schedule, last, requested, changed, now)`: `DAILY`/`WEEKLY`/`MONTHLY` once per calendar day /
  ISO week / month since the last success, `ON_CHANGE` when a certificate changed (version = newest `Data` or `File`
  sample), a request always; after a failed attempt 60 minutes pause (also for requests).
- Tests: `CertificateLogicTest`, `CertificateTransferTest` (export, schedule, HTTP against a local server, the cycle
  with mocks), `CertificateSftpTest` in JENotifier (local MINA server: upload, wrong password, changed server key).

## Web service (`ResourceWebCertificates`, `/JEWebService/v1/web/certificates`)

Same rules as stage E1:
- the user's own rights;
- `version` + `409` on conflict;
- `user_log` entries `…|web:certificate-*`;
- `400` with a plain-text message (validation keys from `CertificateLogic.validate`).

| Call | Purpose |
|---|---|
| `GET /web/certificates?lang=de` (`&site=` optional) | All readable certificates: `id`, `site`, `directory`, all `Data` fields, `status`, `daysLeft`, `file` (`{name, size}` or `null`), `responsibleNames`, `lastReminder`, `lastTransfers`, `canWrite`, `canDelete`, `version`; plus `today`, `directories` (building → directory, `canCreate`) and `transfers` (readable targets: name, channel, format, schedule, `lastTransfer`, `canRun`) |
| `POST /web/certificates?lang=de` | `{ building, data }`: creates the directory if missing (create right on the building), then the certificate. The name is "<type> <issued year>", e.g. "Verbrauchsausweis 2017" |
| `PUT /web/certificates/{id}` | `{ data, version }` → a new `Data` sample |
| `DELETE /web/certificates/{id}?version=` | To the recycle bin (as the other modules) |
| `POST /web/certificates/{id}/file` (multipart) / `GET …/file` / `DELETE …/file?version=` | The PDF (max. 20 MB, `application/pdf` checked by magic bytes) |
| `GET /web/certificates/export?format=CSV\|XLSX&site=` | Download of the overview for the user (no transfer) |
| `POST /web/certificates/transfers/{id}/run` | "Jetzt übermitteln": writes `Requested` = now and user (execute or write right on the target). JENotifier sends it in its next cycle; the answer is `202` |
| `POST /web/certificates/transfers` / `PUT …/{id}` / `DELETE …/{id}` | Transfer targets (write right). Passwords and tokens are write-only: the reads only show `passwordSet: true` |

`GET /web/navigation` answers `modules.certificates` (number of readable certificates) so the menu entry appears only
when there are certificates or a directory the user may create in.

## JENotifier

`CertificateProcessor`, next to `ScheduledMailProcessor`, runs once per cycle:

1. **Reminders.** For each certificate, `dueReminder` decides. The mail goes to `responsible`, resolved like the
   appointment recipients (`MailRecipients.resolve`). Subject "Energieausweis <Gebäude> läuft am <Datum> ab",
   with a link to the web app if "Web App URL" is set. The sent month is written to `Transfer State.remindersSent`.
   If there is no recipient or no address, the error is recorded and the reminder is tried again next cycle.
2. **Transfers.** For each enabled target: due if `Requested` is set, or if `TransferSchedule.isDue` says so.
   - The certificates of its scope go through `CertificateExport` and are sent by the channel:
     - mail with the file attached;
     - SFTP with MINA sshd 2.9.3 (as the DataCollector sFTP driver), host key accepted on first use and stored in
       `Last Transfer`;
     - HTTP POST with `Authorization: Bearer <token>`, TLS checked.
   - The upload goes to `<Path>/<file>.part` and is then renamed, so the receiver never reads a half-written file.
   - Afterwards `Last Transfer` is written and `Requested` is cleared (also after a failure: the error is shown, the
     user requests again). Each sent certificate gets `Transfer State.transfers[<target id>] = { at, version }`. This
     is what `ON_CHANGE` compares against; `ON_CHANGE` sends only the changed certificates.
   - A failing target never stops the others.

## Control Center (J25, plugin "Energieausweise" in the ISO 50001 group)

- Overview table with the same columns as the web app, status colour, filters by site and status, and an export.
- Editing in the side panel (`SidePanel` + `FormLayout`) with these sections:
  - Ausweis;
  - Kennwerte;
  - Gültigkeit und Erinnerung;
  - Zuständig;
  - Datei;
  - Anmerkungen.
- Transfer targets in a second tab, with "Jetzt übermitteln".

## Web app (`/certificates`, group "Anlagen" after Messstellen/Stammdaten/Anlagen; built)

- Overview:
  - search, site filter, status chips (abgelaufen / läuft ab / gültig);
  - columns Gebäude, Art, Registriernummer, Ausstellung, gültig bis (with the remaining days), Klasse,
    Endenergie, Primärenergie, Aussteller, Zuständig, PDF.
- Side panel editor (`CertificateEditDialog`, sections Ausweis, Kennwerte, Gültigkeit und Erinnerung with the
  responsible persons, Datei, Anmerkungen); the type selects the value fields:
  - consumption: heat and electricity consumption;
  - demand: final and primary energy.
- Tab "Übermittlung" with the targets, their last result and "Jetzt übermitteln". The targets are set up in the
  Control Center (they hold passwords).
- The menu entry appears when the user may read at least one certificate (navigation count `certificates`).

## Demo (live, only after a dry-run report and the user's OK)

- Musterwerk:
  - one consumption certificate that is still valid;
  - one demand certificate for "Außenlager Süd" that runs out in about 4 months (status "läuft ab");
  - one expired certificate.
- Neutral names (issuer "Ingenieurbüro Muster"), a PDF generated with the word "MUSTER" across it, and no real
  registration numbers (format "XX-2017-000000001").
- One transfer target "Liegenschaftsamt (Beispiel)", `MAIL`, `MONTHLY`, `CSV`, without recipients (so no mail goes
  out).

## Manual

- New page "Energieausweise" (CC and web app): overview, entering, reminders, transfer.
- Screenshots from the demo; TOC in all pages.

## Order

1. JECommons logic + classes (done).
2. Web service endpoints + tests (done).
3. Web app overview, editor and transfer tab (done).
4. JENotifier reminders and transfers (done: `CertificateProcessor`, `CertificateExport`, `TransferSchedule`,
   `CertificateMails`, `CertificateHttp`, `CertificateSftp`).
5. CC plugin (J25).
6. Demo data (dry run, OK), manual.

Commits only on the user's instruction.
