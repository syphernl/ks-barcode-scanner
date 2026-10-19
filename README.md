# Barcode Scanner

A [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) plugin that scans a barcode with the kiosk's camera and hands the code to Home Assistant.

The **Scan barcode** command opens the Kiosk Satellite scanner over the dashboard. When it reads a code, the plugin fires a `kiosk_barcode_scanned` event in Home Assistant. An event fires on every scan, so scanning the same product twice runs your automation twice, which a "last barcode" sensor would not do. The last code and its format also show under **Readings** on the plugin page and as text sensors.

## Requirements

- Kiosk Satellite 2026.10.30 or newer. Older versions refuse to install the plugin.
- **Settings > Camera > Enable camera** switched on.
- The Home Assistant connection set up in Kiosk Satellite, with an admin user. Home Assistant lets only admins fire events over its websocket API. With a non-admin user the plugin page shows "Could not send the code to Home Assistant" and no event arrives.

## Installation

### Community Plugins (Recommended)

Barcode Scanner is listed in [Community Plugins](https://kiosksatellite.com/plugins/). Use the button below to open it in your Kiosk Satellite and install it.

[![Install in Kiosk Satellite](https://kiosksatellite.com/img/badges/plugin-add.svg)](https://kiosksatellite.com/redirect/plugin/?repository=syphernl/ks-barcode-scanner)

Or search for `Barcode Scanner` in **Settings > Plugin Manager > Browse plugins** in Kiosk Satellite.

### Manual

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/syphernl/ks-barcode-scanner` and choose **Preview**.
3. Review the plugin and choose **Trust and install**.

Enable the plugin from its entry row once it is installed.

## Where data goes

Each scanned code goes to your own Home Assistant as an event, through the connection Kiosk Satellite already has. The plugin sends nothing anywhere else. Scanning happens on the device with ML Kit, inside Kiosk Satellite.

## Tested on

A Lenovo Tab M10 (TB-X606F) with Kiosk Satellite 2026.10.30. Both cameras read product barcodes. The back camera focuses and reads fastest. The front camera has fixed focus, so it reads too, but slower and only once the package is at the right distance.

## Start a scan

Open the plugin's page and pick where **Scan barcode** should live. It can go in the kiosk drawer, on a gesture under **Gestures > Run a plugin action**, or in Home Assistant as an ESPHome button named `button.<kiosk>_barcode_scanner_scan_barcode`. With the button exposed, a dashboard button can start a scan:

```yaml
type: button
name: Scan
icon: mdi:barcode-scan
tap_action:
  action: perform-action
  perform_action: button.press
  target:
    entity_id: button.<kiosk>_barcode_scanner_scan_barcode
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

The plugin page shows why a scan failed: `disabled` means the camera is off in Kiosk Satellite, `permission` means Android never gave it camera access and `busy` means another plugin has a scan open. A scan that times out or that you close is not an error.

## Build

Needs Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools. Set `JAVA_HOME` and `ANDROID_HOME`, and put `$JAVA_HOME/bin` on `PATH` so `d8` finds Java.

```sh
python3 tools/test.py
python3 tools/build.py
```

The build writes the ZIP, its checksum and the manifest to `dist/`. `sdk/` and `tools/` come from the [plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins).

## Releasing

Commits on `main` follow [Conventional Commits](https://www.conventionalcommits.org/): `fix:` for a fix, `feat:` for a new feature. Release Please keeps a release PR open with the next version and the changelog. Merging that PR tags the release, and the same workflow builds the package and attaches it. Kiosk Satellite only accepts release files uploaded by GitHub Actions.

## License

Apache-2.0
