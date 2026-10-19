// SPDX-License-Identifier: Apache-2.0
package nl.syphernl.kiosk.barcode;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class BarcodeScannerPluginTest {
    static final class Host implements PluginHost {
        final Map<String, String> values = new HashMap<>();
        final List<Map<String, Object>> haCalls = new ArrayList<>();
        Map<String, Object> options;
        PluginHost.CommandCallback scan;
        PluginHost.CommandCallback ha;
        String status;
        boolean statusError;
        int scans;
        int cancels;
        public void scanBarcode(Map<String, Object> options, PluginHost.CommandCallback callback) { this.options = options; scan = callback; scans++; }
        public void cancelBarcodeScan() { cancels++; }
        public void publishTextSensor(String key, String name, String value) { values.put(key, value); }
        public void haCall(Map<String, Object> message, PluginHost.CommandCallback callback) { haCalls.add(message); ha = callback; }
        public void status(String message, boolean error) { status = message; statusError = error; }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
    }

    static Map<String, Object> defaults() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("formats", "Product barcodes");
        settings.put("facing", "Back");
        settings.put("timeoutSeconds", 30.0);
        settings.put("title", "Scan a product");
        settings.put("message", "Hold the barcode inside the frame.");
        return settings;
    }

    static Map<String, Object> read(String value, String format) {
        Map<String, Object> data = new HashMap<>();
        data.put("value", value);
        data.put("format", format);
        return data;
    }

    public static void main(String[] args) throws Exception {
        Host host = new Host();
        BarcodeScannerPlugin plugin = new BarcodeScannerPlugin();
        plugin.start(host, defaults());
        assert host.values.containsKey("last_code") && host.values.get("last_code") == null;

        plugin.execute("scan", Collections.emptyMap());
        assert BarcodeScannerPlugin.PRODUCT_FORMATS.equals(host.options.get("formats"));
        assert "back".equals(host.options.get("facing"));
        assert Integer.valueOf(30).equals(host.options.get("timeoutSeconds"));
        assert "Scan a product".equals(host.options.get("title"));
        // A second press while the scanner is open leaves it open.
        plugin.execute("scan", Collections.emptyMap());
        assert host.scans == 1;

        host.scan.onResult(true, read("8710400311515", "ean_13"), null);
        assert "8710400311515".equals(host.values.get("last_code"));
        assert "ean_13".equals(host.values.get("last_code_format"));
        Map<String, Object> event = host.haCalls.get(0);
        assert "fire_event".equals(event.get("type")) && BarcodeScannerPlugin.EVENT.equals(event.get("event_type"));
        assert "8710400311515".equals(((Map<?, ?>) event.get("event_data")).get("value"));
        assert "ean_13".equals(((Map<?, ?>) event.get("event_data")).get("format"));
        host.ha.onResult(true, null, null);
        assert !host.statusError;

        // The same product again fires a second event.
        plugin.execute("scan", Collections.emptyMap());
        host.scan.onResult(true, read("8710400311515", "ean_13"), null);
        assert host.haCalls.size() == 2;
        host.ha.onResult(false, null, "not_connected");
        assert host.statusError && host.status.contains("not_connected");

        // A scan that ends without a code keeps the last one and fires nothing.
        plugin.execute("scan", Collections.emptyMap());
        host.scan.onResult(false, Collections.singletonMap("code", "timeout"), "timeout");
        assert "8710400311515".equals(host.values.get("last_code"));
        assert host.haCalls.size() == 2;
        plugin.execute("scan", Collections.emptyMap());
        host.scan.onResult(false, Collections.singletonMap("code", "disabled"), "disabled");
        assert host.statusError && host.status.contains("disabled");

        // Close scanner only acts on an open scan.
        plugin.execute("cancel", Collections.emptyMap());
        assert host.cancels == 0;
        plugin.execute("scan", Collections.emptyMap());
        plugin.execute("cancel", Collections.emptyMap());
        assert host.cancels == 1;
        host.scan.onResult(false, Collections.singletonMap("code", "canceled"), "canceled");

        // Settings map to scanner options.
        Map<String, Object> settings = defaults();
        settings.put("formats", "All codes");
        settings.put("facing", "Camera settings");
        settings.put("timeoutSeconds", 60.0);
        settings.put("title", "  ");
        plugin.configure(settings);
        plugin.execute("scan", Collections.emptyMap());
        assert !host.options.containsKey("formats");
        assert "default".equals(host.options.get("facing"));
        assert Integer.valueOf(60).equals(host.options.get("timeoutSeconds"));
        assert !host.options.containsKey("title");
        settings.put("facing", "Front");
        assert "front".equals(BarcodeScannerPlugin.scanOptions(settings).get("facing"));

        try {
            plugin.execute("nope", Collections.emptyMap());
            assert false;
        } catch (IllegalArgumentException expected) {}

        // After stop a late result is ignored.
        plugin.stop();
        host.scan.onResult(true, read("1", "ean_8"), null);
        assert host.haCalls.size() == 2;
        System.out.println("Barcode scanner checks passed.");
    }
}
