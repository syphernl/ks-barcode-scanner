// SPDX-License-Identifier: Apache-2.0
package nl.syphernl.kiosk.barcode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * The Scan barcode command opens the KS scanner. Every code it reads fires a
 * kiosk_barcode_scanned event in Home Assistant, so scanning the same product
 * twice still triggers an automation twice. The last code also shows under
 * Readings and as a text sensor, and a scan counter goes up on every read for
 * setups that use the sensors instead of the event.
 */
public final class BarcodeScannerPlugin implements KioskPlugin {
    static final String EVENT = "kiosk_barcode_scanned";
    static final List<String> PRODUCT_FORMATS = Arrays.asList("ean_13", "ean_8", "upc_a", "upc_e");
    private static final int MAX_SENSOR_LENGTH = 512;
    private static final Map<String, Object> COUNT_METADATA = new LinkedHashMap<>();
    static { COUNT_METADATA.put("accuracyDecimals", 0); }

    private PluginHost host;
    private Map<String, Object> options;
    private boolean scanning;
    private boolean sendEvent;
    private int count;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        configure(settings);
        host.publishTextSensor("last_code", "Last code", null);
        host.publishTextSensor("last_code_format", "Last code format", null);
        count = 0;
        host.publishSensor("scan_count", "Scan count", COUNT_METADATA, 0.0);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        options = scanOptions(settings);
        // Missing on an upgrade from 0.2, when the event was the only output.
        sendEvent = !Boolean.FALSE.equals(settings.get("sendEvent"));
    }

    static Map<String, Object> scanOptions(Map<String, Object> settings) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (!"All codes".equals(settings.get("formats"))) options.put("formats", PRODUCT_FORMATS);
        Object facing = settings.get("facing");
        options.put("facing", "Front".equals(facing) ? "front" : "Camera settings".equals(facing) ? "default" : "back");
        options.put("timeoutSeconds", ((Number) settings.get("timeoutSeconds")).intValue());
        // The scanner takes up to 80 and 200 characters; a string setting holds 512.
        putText(options, "title", settings.get("title"), 80);
        putText(options, "message", settings.get("message"), 200);
        return options;
    }

    /** An empty text leaves the KS default in place. */
    private static void putText(Map<String, Object> options, String key, Object value, int max) {
        if (!(value instanceof String)) return;
        String text = ((String) value).trim();
        if (!text.isEmpty()) options.put(key, text.length() > max ? text.substring(0, max) : text);
    }

    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        if ("scan".equals(command)) scan();
        else if ("cancel".equals(command)) cancel();
        else throw new IllegalArgumentException("Unknown command: " + command);
    }

    /** A scan already open stays open. */
    private void scan() {
        if (host == null || scanning) return;
        scanning = true;
        try {
            host.scanBarcode(new LinkedHashMap<>(options), this::finished);
        } catch (RuntimeException rejected) {
            // A rejected scan must not leave scanning set, or every later press is ignored.
            scanning = false;
            host.status("Could not open the scanner: " + rejected.getMessage(), true);
        }
    }

    private void cancel() {
        if (host == null || !scanning) return;
        host.cancelBarcodeScan();
    }

    private synchronized void finished(boolean ok, Object data, String error) {
        scanning = false;
        if (host == null) return;
        if (!ok) {
            // canceled and timeout are normal endings. The rest needs the user's attention.
            if ("canceled".equals(error) || "timeout".equals(error)) host.log("Scan ended: " + error);
            else host.status("Scan failed: " + error, true);
            return;
        }
        Map<?, ?> result = (Map<?, ?>) data;
        String value = (String) result.get("value");
        String format = (String) result.get("format");
        host.publishTextSensor("last_code", "Last code", value.length() > MAX_SENSOR_LENGTH ? value.substring(0, MAX_SENSOR_LENGTH) : value);
        host.publishTextSensor("last_code_format", "Last code format", format);
        // After the code, so a trigger on the count reads the new code.
        host.publishSensor("scan_count", "Scan count", COUNT_METADATA, (double) ++count);
        if (!sendEvent) {
            host.status("Read a " + format + " code.", false);
            return;
        }
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("value", value);
        eventData.put("format", format);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "fire_event");
        message.put("event_type", EVENT);
        message.put("event_data", eventData);
        PluginHost current = host;
        // The code itself stays out of the status: a QR code can carry a password.
        current.haCall(message, (sent, reply, failure) -> report(current,
            sent ? "Sent a " + format + " code to Home Assistant." : "Could not send the code to Home Assistant: " + failure, !sent));
    }

    /** A reply can arrive after stop, when the host it came through is revoked. */
    private synchronized void report(PluginHost from, String message, boolean error) {
        if (host == from) host.status(message, error);
    }

    @Override
    public void onEvent(String event, Map<String, Object> payload) {}

    @Override
    public synchronized void stop() {
        host = null;
        scanning = false;
    }
}
