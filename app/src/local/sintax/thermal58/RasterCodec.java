package local.sintax.thermal58;
import java.io.*;
public final class RasterCodec {
    public static void page(ByteArrayOutputStream out,int[] pixels,int width,int height) {
        if(width!=384 || height<1 || pixels.length!=width*height) throw new IllegalArgumentException("Invalid raster");
        int first=height,last=-1;
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) if(black(pixels[y*width+x])) { first=Math.min(first,y); last=y; }
        if(last<0) return;
        for(int top=first;top<=last;top+=128) {
            int rows=Math.min(128,last-top+1);
            byte[] header={29,118,48,0,48,0,(byte)rows,(byte)(rows>>8)};
            out.write(header,0,header.length);
            for(int y=top;y<top+rows;y++) for(int x=0;x<width;x+=8) {
                int value=0;
                for(int bit=0;bit<8;bit++) if(black(pixels[y*width+x+bit])) value|=128>>bit;
                out.write(value);
            }
        }
    }
    static boolean black(int rgb) { return (((rgb>>16)&255)*299+((rgb>>8)&255)*587+(rgb&255)*114)<160000; }
    public static void main(String[] args) {
        int[] p=new int[384*130]; java.util.Arrays.fill(p,0xffffffff);
        ByteArrayOutputStream b=new ByteArrayOutputStream(); page(b,p,384,130);
        if(b.size()!=0) throw new AssertionError("blank page");
        p[384+0]=0xff000000; p[384+7]=0xff000000; p[384+8]=0xff000000;
        page(b,p,384,130); byte[] data=b.toByteArray();
        if(data.length!=56 || (data[8]&255)!=129 || (data[9]&255)!=128 || data[6]!=1) throw new AssertionError("bits/trim");
        b.reset(); java.util.Arrays.fill(p,0xff000000); page(b,p,384,130); data=b.toByteArray();
        if(data.length!=384*130/8+16 || (data[6]&255)!=128 || data[8+128*48+6]!=2) throw new AssertionError("strips");
        System.out.println("Raster codec self-checks passed.");
    }
}
