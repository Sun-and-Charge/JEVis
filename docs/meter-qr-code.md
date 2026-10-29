# Meter QR codes

The Meters plugin of the Control Center creates a QR code for every meter (Measurement Instrument object).
A mobile app for manual readings scans the code, selects the meter and posts the reading to the JEWebService.
The code is computed from the meter's data whenever it is shown; nothing is stored on the meter.

Where to get the codes in the Control Center:

- **Toolbar → QR button**: PDF label sheet for the selected meters, or for all meters in the current (filtered) list
  if none is selected. Layout: A4, 3 × 8 labels of 70 × 37 mm (e.g. Avery Zweckform 3474).
- **QR column** in the meter table: opens the code of one meter (save as PNG, label as PDF, copy).
- **Meter form → "QR code" tab**: the same view.

## Payload, version 1

```
jevis:meter?v=1&id=<meterId>[&target=<objectId>:<attribute>][&server=<url>]
```

Example: `jevis:meter?v=1&id=4711&target=5123:Value&server=https%3A%2F%2Fsunandcharge.app%3A443`

| Parameter | Required | Meaning                                                                                       |
|-----------|----------|-----------------------------------------------------------------------------------------------|
| `v`       | yes      | Payload version. Apps must reject codes with an unknown version.                              |
| `id`      | yes      | Object ID of the meter (Measurement Instrument).                                              |
| `target`  | no       | Data row the readings belong to, taken from the meter's `Online ID` attribute. The attribute name is URL-encoded; if the Online ID points to an object only, `Value` is used. Missing if no Online ID is set. |
| `server`  | no       | Base URL of the JEWebService the Control Center was connected to (URL-encoded).               |

Values are URL-encoded (UTF-8, space as `%20`). Apps should ignore unknown parameters so that new optional
parameters can be added without a version change.

## App workflow

1. Scan and parse the payload; check `v`.
2. Log in to the server (from the app's configuration; `server` can be used to preselect or check it).
   The REST API accepts Basic auth or a session token like the Control Center.
3. Check the meter: `GET /JEWebService/v1/objects/{id}`. Show its name to the user.
4. Resolve the target:
   - if `target` is present, use it, but it may be outdated if the meter's Online ID was changed after printing.
     Optionally compare with `GET /JEWebService/v1/objects/{id}/attributes/Online%20ID/samples?onlyLatest=true`
     (sample value has the form `<objectId>:<attribute>`);
   - if `target` is missing, use that request to look it up. Without an Online ID the meter cannot take readings.
5. Show the last reading for plausibility:
   `GET /JEWebService/v1/objects/{targetId}/attributes/{attribute}/samples?onlyLatest=true`.
6. Post the reading:

```
POST /JEWebService/v1/objects/{targetId}/attributes/{attribute}/samples
Content-Type: application/json

[{"ts": "2026-10-29T10:15:00.000+01:00", "value": "12345.6", "note": "manual reading (app)"}]
```

`ts` is ISO 8601 with offset (as written by `JsonFactory.sampleDTF`), `value` is sent as string, `note` is optional.
The user needs write permission on the target object.
