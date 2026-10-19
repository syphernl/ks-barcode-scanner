# Barcode Scanner

A [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) plugin that scans a barcode with the kiosk's camera and hands the code to Home Assistant.

The **Scan barcode** command opens the Kiosk Satellite scanner over the dashboard. When it reads a code, the plugin fires a `kiosk_barcode_scanned` event in Home Assistant. An event fires on every scan, so scanning the same product twice runs your automation twice, which a "last barcode" sensor would not do. The last code and its format also show under **Readings** on the plugin page and as text sensors.

## Requirements

- Kiosk Satellite with the plugin `barcode` capability (the release after 2026.10.29). Older versions refuse to install the plugin.
- **Settings > Camera > Enable camera** switched on.
- The Home Assistant connection set up in Kiosk Satellite.

## Install

Build the ZIP (see below), then open **Plugin Manager > Developer Tools > Install from ZIP** on the kiosk or in Remote Admin and pick `dist/barcode-scanner-<version>.zip`. Enable the plugin from its entry row.

## Start a scan

Open the plugin's page and pick where **Scan barcode** should live. It can go in the kiosk drawer, on a gesture under **Gestures > Run a plugin action**, or in Home Assistant as an ESPHome button. With the button exposed, a dashboard button can start a scan:

```yaml
type: button
name: Scan
icon: mdi:barcode-scan
tap_action:
  action: perform-action
  perform_action: button.press
  target:
    entity_id: button.<kiosk>_scan_barcode
```

**Close scanner** closes an open scan.

## Use the result

The event carries the code and its format:

```yaml
triggers:
  - trigger: event
    event_type: kiosk_barcode_scanned
actions:
  - action: notify.persistent_notification
    data:
      message: "{{ trigger.event.data.format }}: {{ trigger.event.data.value }}"
```

`format` is one of `ean_13`, `ean_8`, `upc_a`, `upc_e`, or with **All codes** also `qr_code`, `aztec`, `data_matrix`, `pdf417`, `code_128`, `code_39`, `code_93`, `codabar` and `itf`.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Codes | Product barcodes | Product barcodes reads EAN-13, EAN-8, UPC-A and UPC-E. All codes reads every format, QR included. The scanner ignores a format left off the list and keeps waiting |
| Camera | Back | The lens the scanner opens with. Camera settings uses the lens picked in Kiosk Satellite. The flip button in the scanner switches it |
| Timeout | 30 s | The scanner closes after 5 to 120 seconds without a read |
| Scanner title, Scanner message | Scan a product, Hold the barcode inside the frame. | The text in the scanner. Empty falls back to the Kiosk Satellite text |

A front camera with fixed focus often can't read a package held close. Use the back camera on such a tablet.

The plugin page shows why a scan failed: `disabled` means the camera is off in Kiosk Satellite, `permission` means Android never gave it camera access and `busy` means another plugin has a scan open. A scan that times out or that you close is not an error.

## Build

Needs Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools. Set `JAVA_HOME` and `ANDROID_HOME`, and put `$JAVA_HOME/bin` on `PATH` so `d8` finds Java.

```sh
python3 tools/test.py
python3 tools/build.py
```

The build writes the ZIP, its checksum and the manifest to `dist/`. `sdk/` and `tools/` come from the [plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins).

## License

Apache-2.0
