package local.sintax.thermal58;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.print.*;
import android.printservice.*;
import android.printservice.PrintJob;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReceiptService extends PrintService {
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Map<PrintJobId,AtomicBoolean> active=new HashMap<>();
    @Override protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new PrinterDiscoverySession() {
            void publish() {
                String address=getSharedPreferences("printer",0).getString("address",null);
                if(address==null) return;
                PrinterId id=generatePrinterId("receipt58");
                PrinterCapabilitiesInfo caps=new PrinterCapabilitiesInfo.Builder(id)
                    .addMediaSize(new PrintAttributes.MediaSize("receipt58x500","58 × 500 mm receipt",2283,19685),true)
                    .addMediaSize(new PrintAttributes.MediaSize("receipt58x200","58 × 200 mm receipt",2283,7874),false)
                    .addResolution(new PrintAttributes.Resolution("203dpi","203 dpi",203,203),true)
                    .setMinMargins(new PrintAttributes.Margins(197,0,197,0))
                    .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME,PrintAttributes.COLOR_MODE_MONOCHROME)
                    .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE,PrintAttributes.DUPLEX_MODE_NONE).build();
                addPrinters(Collections.singletonList(new PrinterInfo.Builder(id,"58mm Receipt Printer",PrinterInfo.STATUS_IDLE).setDescription("PRINTER • 384 dots • Bluetooth").setCapabilities(caps).build()));
            }
            @Override public void onStartPrinterDiscovery(List<PrinterId> priorities) { publish(); }
            @Override public void onStopPrinterDiscovery() {}
            @Override public void onValidatePrinters(List<PrinterId> ids) { publish(); }
            @Override public void onStartPrinterStateTracking(PrinterId id) { publish(); }
            @Override public void onStopPrinterStateTracking(PrinterId id) {}
            @Override public void onDestroy() {}
        };
    }
    @Override protected void onPrintJobQueued(PrintJob job) {
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) { job.fail("Open 58mm Receipt Printer and allow Nearby devices."); return; }
        String address=getSharedPreferences("printer",0).getString("address",null);
        if(address==null) { job.fail("Open 58mm Receipt Printer and select PRINTER first."); return; }
        if(!job.start()) return;
        AtomicBoolean cancelled=new AtomicBoolean(false); PrintJobId id=job.getId(); active.put(id,cancelled);
        ParcelFileDescriptor source=job.getDocument().getData();
        int copies=job.getInfo().getCopies();
        PageRange[] pages=job.getInfo().getPages();
        worker.execute(() -> {
            File pdf=null; String error=null;
            try {
                if(source==null) throw new IOException("No print document received.");
                if(copies<1 || copies>10) throw new IOException("Choose between 1 and 10 copies.");
                pdf=File.createTempFile("receipt-",".pdf",getCacheDir());
                try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(source); OutputStream out=new FileOutputStream(pdf)) {
                    byte[] buffer=new byte[16384]; int n,total=0;
                    while((n=in.read(buffer))!=-1) { if(cancelled.get()) throw new IOException("Cancelled."); total+=n; if(total>20*1024*1024) throw new IOException("PDF exceeds 20 MB."); out.write(buffer,0,n); }
                }
                byte[] data=render(pdf,pages,cancelled);
                if(cancelled.get()) throw new IOException("Cancelled.");
                PrinterTransport.send(this,address,data,copies,cancelled);
            } catch(Exception e) { error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
            finally { if(pdf!=null) pdf.delete(); }
            final String message=error;
            main.post(() -> {
                active.remove(id);
                if(cancelled.get()) job.cancel();
                else if(message!=null) job.fail(message+" No automatic retry.");
                else job.complete();
            });
        });
    }
    @Override protected void onRequestCancelPrintJob(PrintJob job) {
        AtomicBoolean flag=active.get(job.getId());
        if(flag!=null) flag.set(true); else job.cancel();
    }
    static boolean selected(int page,PageRange[] ranges) {
        if(ranges==null || ranges.length==0) return true;
        for(PageRange range:ranges) if(page>=range.getStart() && page<=range.getEnd()) return true;
        return false;
    }
    static byte[] render(File file,PageRange[] ranges,AtomicBoolean cancelled) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); out.write(new byte[]{27,64});
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer pdf=new PdfRenderer(fd)) {
            if(pdf.getPageCount()>50) throw new IOException("Too many pages. Print at most 50 pages per job.");
            for(int i=0;i<pdf.getPageCount();i++) {
                if(!selected(i,ranges)) continue;
                if(cancelled.get()) throw new IOException("Cancelled.");
                try(PdfRenderer.Page page=pdf.openPage(i)) {
                    if(page.getWidth()>180) throw new IOException("Page is wider than a receipt. Choose 58 × 500 mm, then reopen Print.");
                    double scale=464.0/page.getWidth(); int height=(int)Math.ceil(page.getHeight()*scale);
                    if(height<1 || height>8500) throw new IOException("Page height unsupported. Choose 58 × 500 mm.");
                    Bitmap bitmap=Bitmap.createBitmap(464,height,Bitmap.Config.ARGB_8888);
                    try {
                        bitmap.eraseColor(Color.WHITE);
                        Matrix matrix=new Matrix(); matrix.setScale((float)scale,(float)scale);
                        page.render(bitmap,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        Bitmap printable=fitContent(bitmap);
                        ByteArrayOutputStream part=new ByteArrayOutputStream();
                        try {
                            int rows=printable.getHeight(); int[] pixels=new int[384*rows]; printable.getPixels(pixels,0,384,0,0,384,rows);
                            RasterCodec.page(part,pixels,384,rows);
                        } finally { printable.recycle(); }
                        int before=out.size();
                        if(part.size()>0) {
                            if(before>2) out.write(new byte[]{27,74,16});
                            part.writeTo(out);
                        }
                    } finally { bitmap.recycle(); }
                }
            }
        }
        out.write(new byte[]{10,10,10}); return out.toByteArray();
    }
    static Bitmap fitContent(Bitmap source) {
        int width=source.getWidth(),height=source.getHeight();
        int[] pixels=new int[width*height]; source.getPixels(pixels,0,width,0,0,width,height);
        int left=width,right=-1;
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) if(RasterCodec.black(pixels[y*width+x])) { left=Math.min(left,x);right=Math.max(right,x); }
        int crop=40;
        if(right>=0 && (left<40 || right>=424)) {
            int inkWidth=right-left+1;
            if(inkWidth>384) {
                int rows=Math.max(1,Math.round(height*384f/inkWidth));
                Bitmap fitted=Bitmap.createBitmap(384,rows,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(fitted);canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(source,new Rect(left,0,right+1,height),new Rect(0,0,384,rows),null);
                return fitted;
            }
            crop=Math.max(0,Math.min(width-384,(left+right+1-384)/2));
        }
        return Bitmap.createBitmap(source,crop,0,384,height);
    }
    @Override public void onDestroy() { for(AtomicBoolean flag:active.values()) flag.set(true); worker.shutdown(); super.onDestroy(); }
}
