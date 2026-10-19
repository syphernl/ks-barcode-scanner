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
 * Readings and as a text sensor.
 */
public final class BarcodeScannerPlugin implements KioskPlugin {
    static final String EVENT = "kiosk_barcode_scanned";
    static final List<String> PRODUCT_FORMATS = Arrays.asList("ean_13", "ean_8", "upc_a", "upc_e");
    private static final int MAX_SENSOR_LENGTH = 512;

    private PluginHost host;
    private Map<String, Object> options;
    private boolean scanning;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        configure(settings);
        host.publishTextSensor("last_code", "Last code", null);
        host.publishTextSensor("last_code_format", "Last code format", null);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        options = scanOptions(settings);
    }

    static Map<String, Object> scanOptions(Map<String, Object> settings) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (!"All codes".equals(settings.get("formats"))) options.put("formats", PRODUCT_FORMATS);
        Object facing = settings.get("facing");
        options.put("facing", "Front".equals(facing) ? "front" : "Camera settings".equals(facing) ? "default" : "back");
        options.put("timeoutSeconds", ((Number) settings.get("timeoutSeconds")).intValue());
        putText(options, "title", settings.get("title"));
        putText(options, "message", settings.get("message"));
        return options;
    }

    /** An empty text leaves the KS default in place. */
    private static void putText(Map<String, Object> options, String key, Object value) {
        if (value instanceof String && !((String) value).trim().isEmpty()) options.put(key, ((String) value).trim());
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
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            scanning = false;
            host.status("Barcode scanning needs a newer Kiosk Satellite.", true);
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
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("value", value);
        eventData.put("format", format);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "fire_event");
        message.put("event_type", EVENT);
        message.put("event_data", eventData);
        PluginHost current = host;
        current.haCall(message, (sent, reply, failure) -> {
            if (sent) current.status("Sent " + format + " " + value + " to Home Assistant.", false);
            else current.status("Could not send the code to Home Assistant: " + failure, true);
        });
    }

    @Override
    public void onEvent(String event, Map<String, Object> payload) {}

    @Override
    public synchronized void stop() {
        host = null;
        scanning = false;
    }
}
