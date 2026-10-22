package local.sintax.thermal58;
import android.bluetooth.*;
import android.content.Context;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class PrinterTransport {
    static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    static void send(Context context,String address,byte[] data,int copies,AtomicBoolean cancelled) throws Exception {
        BluetoothAdapter adapter=context.getSystemService(BluetoothManager.class).getAdapter();
        if(adapter==null || !adapter.isEnabled()) throw new IOException("Turn on Bluetooth.");
        BluetoothDevice device=adapter.getRemoteDevice(address);
        int type=device.getType();
        if(device.getBondState()==BluetoothDevice.BOND_BONDED && (type==BluetoothDevice.DEVICE_TYPE_CLASSIC || type==BluetoothDevice.DEVICE_TYPE_DUAL)) {
            BluetoothSocket socket=device.createRfcommSocketToServiceRecord(SPP);
            ScheduledExecutorService guard=Executors.newSingleThreadScheduledExecutor();
            AtomicBoolean timedOut=new AtomicBoolean(false);
            ScheduledFuture<?> deadline=guard.schedule(() -> { timedOut.set(true); try { socket.close(); } catch(IOException ignored) {} },15,TimeUnit.SECONDS);
            ScheduledFuture<?> cancellation=guard.scheduleAtFixedRate(() -> { if(cancelled.get()) try { socket.close(); } catch(IOException ignored) {} },0,200,TimeUnit.MILLISECONDS);
            boolean connected=false;
            try {
                try { socket.connect(); connected=true; }
                catch(IOException connectionFailure) {
                    if(cancelled.get()) throw new IOException("Cancelled. Check paper before retrying.");
                    // Only try BLE when Classic connection failed before any print bytes were sent.
                }
                deadline.cancel(false);
                if(connected && !timedOut.get()) {
                    OutputStream stream=socket.getOutputStream();
                    for(int copy=0;copy<copies;copy++) for(int offset=0;offset<data.length;) {
                        if(cancelled.get()) throw new IOException("Cancelled. Check paper before retrying.");
                        int count=Math.min(512,data.length-offset);
                        stream.write(data,offset,count); stream.flush(); offset+=count;
                        Thread.sleep(Math.max(5,(count*1000L+7999)/8000));
                    }
                    return;
                }
            } finally {
                deadline.cancel(false); cancellation.cancel(false); guard.shutdownNow(); try { socket.close(); } catch(IOException ignored) {}
            }
        }
        if(cancelled.get()) throw new IOException("Cancelled.");
        try(BlePrinter printer=new BlePrinter(context,address,cancelled)) {
            for(int copy=0;copy<copies;copy++) printer.send(data);
        }
    }
}
