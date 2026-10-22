package local.sintax.thermal58;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.print.*;
import android.print.pdf.PrintedPdfDocument;
import android.graphics.*;
import java.io.*;
import android.view.View;
import android.widget.*;
import java.util.*;

public class SetupActivity extends Activity {
    final Handler handler = new Handler(Looper.getMainLooper());
    final Set<String> seen = new HashSet<>();
    TextView status;
    LinearLayout printers;
    BluetoothLeScanner scanner;
    boolean scanning;
    final Runnable stop = () -> stopScan();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(28,28,28,28);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("58mm Receipt Printer"); title.setTextSize(25); root.addView(title);
        TextView help = new TextView(this);
        help.setText("1. Keep PRINTER on nearby. Disconnect its phone/Mac connection.\n2. Scan and tap your printer once.\n3. Enable this print service, then select it in Chrome.\n\nUse 58 × 500 mm paper for bills. No A4 scaling. This app needs no internet access.");
        help.setTextSize(17); root.addView(help);
        status = new TextView(this); status.setTextSize(18); root.addView(status); showSaved();
        button(root,"Scan for printer", v -> scan());
        printers = new LinearLayout(this); printers.setOrientation(1); root.addView(printers);
        button(root,"Enable print service", v -> startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS)));
        button(root,"Print size test", v -> calibration());
        TextView note = new TextView(this);
        note.setText("After enabling, return to Chrome or your billing app → Print → 58mm Receipt Printer. Start with one copy. Failed or cancelled jobs are never automatically replayed. If a job partly prints, inspect the paper before manually retrying."); root.addView(note);
    }
    void calibration() {
        PrintDocumentAdapter adapter=new PrintDocumentAdapter() {
            PrintAttributes attributes;
            @Override public void onLayout(PrintAttributes old,PrintAttributes next,CancellationSignal cancel,LayoutResultCallback callback,Bundle extras) {
                attributes=next;
                if(cancel.isCanceled()) callback.onLayoutCancelled();
                else callback.onLayoutFinished(new PrintDocumentInfo.Builder("58mm size test").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);
            }
            @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,CancellationSignal cancel,WriteResultCallback callback) {
                if(cancel.isCanceled()) { callback.onWriteCancelled(); return; }
                PrintedPdfDocument pdf=new PrintedPdfDocument(SetupActivity.this,attributes);
                try(OutputStream out=new FileOutputStream(destination.getFileDescriptor())) {
                    android.graphics.pdf.PdfDocument.Page page=pdf.startPage(0);
                    Canvas canvas=page.getCanvas(); Paint paint=new Paint(); paint.setColor(Color.BLACK); paint.setTextSize(16);
                    canvas.drawText("RECEIPT TEST",16,35,paint);
                    canvas.drawText("Large text: 16 pt",16,65,paint);
                    paint.setStrokeWidth(1); canvas.drawLine(22,90,22+40*72f/25.4f,90,paint);
                    paint.setTextSize(12); canvas.drawText("Line should be 40 mm",16,115,paint);
                    pdf.finishPage(page); pdf.writeTo(out);
                    callback.onWriteFinished(new PageRange[]{new PageRange(0,0)});
                } catch(Exception e) { callback.onWriteFailed(e.getMessage()); } finally { pdf.close(); }
            }
        };
        PrintAttributes attributes=new PrintAttributes.Builder()
            .setMediaSize(new PrintAttributes.MediaSize("receipt58x200","58 × 200 mm receipt",2283,7874))
            .setResolution(new PrintAttributes.Resolution("203dpi","203 dpi",203,203))
            .setMinMargins(new PrintAttributes.Margins(197,0,197,0))
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build();
        getSystemService(PrintManager.class).print("58mm size test",adapter,attributes);
    }
    void button(LinearLayout root,String label,View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(action); root.addView(b);
    }
    void showSaved() {
        String name = getSharedPreferences("printer",0).getString("name",null);
        status.setText(name == null ? "No printer selected." : "Selected: " + name);
    }
    boolean allowed() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    }
    void scan() {
        if (!allowed()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},1); return; }
        BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
        if (adapter == null || !adapter.isEnabled()) { status.setText("Turn on Bluetooth, then Scan."); return; }
        stopScan(); seen.clear(); printers.removeAllViews();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { status.setText("Bluetooth scanner unavailable."); return; }
        scanning = true; status.setText("Scanning for PRINTER…");
        scanner.startScan(callback); handler.postDelayed(stop,15000);
    }
    final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type,ScanResult result) {
            handler.post(() -> {
                if (!scanning || !allowed()) return;
                BluetoothDevice d=result.getDevice(); ScanRecord record=result.getScanRecord();
                String name=record==null?null:record.getDeviceName(); if(name==null) name=d.getName();
                boolean service=record!=null && record.getServiceUuids()!=null && record.getServiceUuids().contains(new ParcelUuid(BlePrinter.SERVICE));
                if (!"PRINTER".equalsIgnoreCase(name) && !service) return;
                if (!seen.add(d.getAddress())) return;
                final String label=name==null?"Receipt printer":name;
                button(printers,label+"  "+d.getAddress(),v -> {
                    getSharedPreferences("printer",0).edit().putString("address",d.getAddress()).putString("name",label).apply();
                    stopScan(); showSaved();
                });
            });
        }
        @Override public void onScanFailed(int error) { handler.post(() -> { stopScan(); status.setText("Scan failed ("+error+"). Try turning Bluetooth off/on."); }); }
    };
    void stopScan() {
        handler.removeCallbacks(stop);
        if(scanning && scanner!=null && allowed()) scanner.stopScan(callback);
        if(scanning && status!=null) status.setText(seen.isEmpty()?"No printer found. Check power and other connections.":"Tap your printer above, then enable the print service.");
        scanning=false;
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(allowed()) scan(); else status.setText("Nearby devices permission is needed to connect to your printer.");
    }
    @Override public void onStop() { stopScan(); super.onStop(); }
}
