package local.sintax.thermal58;
import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.print.PageRange;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class SelfCheck extends Instrumentation {
 @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
 @Override public void onStart() {
  Bundle result=new Bundle(); File file=null;
  try {
   RasterCodec.main(new String[0]);
   file=new File(getTargetContext().getCacheDir(),"self-check.pdf");
   PdfDocument document=new PdfDocument();
   for(int i=0;i<2;i++) {
    PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(164,567,i).create());
    Paint paint=new Paint();paint.setColor(Color.BLACK);
    page.getCanvas().drawRect(22,30,22+40*72f/25.4f,35,paint);
    document.finishPage(page);
   }
   try(OutputStream out=new FileOutputStream(file)) { document.writeTo(out); } finally { document.close(); }
   byte[] bytes=ReceiptService.render(file,new PageRange[]{PageRange.ALL_PAGES},new AtomicBoolean(false));
   int offset=2,strips=0,joins=0;
   while(offset+8<bytes.length) {
    if(bytes[offset]==27 && bytes[offset+1]==74) { joins++;offset+=3;continue; }
    if(bytes[offset]!=29 || bytes[offset+1]!=118) break;
    int rows=(bytes[offset+6]&255)+256*(bytes[offset+7]&255);
    if(rows<12 || rows>16) throw new AssertionError("Physical height/blank trim");
    int black=0;for(int x=0;x<48;x++) black+=Integer.bitCount(bytes[offset+8+x]&255);
    if(black<319 || black>323) throw new AssertionError("40mm line scaled incorrectly: "+black);
    offset+=8+rows*48;strips++;
   }
   if(strips!=2 || joins!=1 || bytes.length>2000) throw new AssertionError("Page join/trim");
   byte[] single=ReceiptService.render(file,new PageRange[]{new PageRange(1,1)},new AtomicBoolean(false));
   if(single.length>=bytes.length) throw new AssertionError("Page selection");
   boolean cancelled=false;
   try { ReceiptService.render(file,null,new AtomicBoolean(true)); } catch(IOException e) { cancelled=true; }
   if(!cancelled) throw new AssertionError("Cancellation");
   Bitmap wide=Bitmap.createBitmap(464,40,Bitmap.Config.ARGB_8888);wide.eraseColor(Color.WHITE);
   Canvas c=new Canvas(wide);Paint blackPaint=new Paint();blackPaint.setColor(Color.BLACK);
   c.drawRect(0,0,12,8,blackPaint);c.drawRect(452,28,464,36,blackPaint);
   Bitmap fitted=ReceiptService.fitContent(wide);
   if(fitted.getWidth()!=384 || !RasterCodec.black(fitted.getPixel(1,1)) || !RasterCodec.black(fitted.getPixel(382,26))) throw new AssertionError("Both page edges must survive fitting");
   fitted.recycle();wide.recycle();
   Bitmap shifted=Bitmap.createBitmap(464,40,Bitmap.Config.ARGB_8888);shifted.eraseColor(Color.WHITE);
   new Canvas(shifted).drawRect(5,5,300,15,blackPaint);
   Bitmap shiftedFit=ReceiptService.fitContent(shifted);
   if(shiftedFit.getHeight()!=40 || !RasterCodec.black(shiftedFit.getPixel(5,6))) throw new AssertionError("Shift narrow content without shrinking");
   shiftedFit.recycle();shifted.recycle();
   result.putString("result","PASS: actual Android PDF scaling, 40mm raster line, blank trimming, compact page joins, page selection, cancellation, bit packing and preservation of both overflowing edges");
   finish(Activity.RESULT_OK,result);
  } catch(Throwable e) { result.putString("result","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result); }
  finally { if(file!=null) file.delete(); }
 }
}
