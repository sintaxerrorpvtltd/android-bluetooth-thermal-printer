package local.sintax.thermal58;
import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class BlePrinter extends BluetoothGattCallback implements AutoCloseable {
    static final UUID SERVICE=UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb");
    static final UUID WRITE=UUID.fromString("00002af1-0000-1000-8000-00805f9b34fb");
    final CountDownLatch ready=new CountDownLatch(1);
    volatile CountDownLatch writeDone;
    volatile String failure;
    volatile int writeStatus;
    volatile int packetSize=20;
    BluetoothGatt gatt;
    BluetoothGattCharacteristic characteristic;
    final AtomicBoolean cancelled;
    BlePrinter(Context context,String address,AtomicBoolean cancelled) throws Exception {
        this.cancelled=cancelled;
        BluetoothAdapter adapter=context.getSystemService(BluetoothManager.class).getAdapter();
        if(adapter==null || !adapter.isEnabled()) throw new IOException("Turn on Bluetooth.");
        BluetoothDevice device=adapter.getRemoteDevice(address);
        gatt=device.connectGatt(context,false,this,BluetoothDevice.TRANSPORT_LE);
        if(gatt==null) throw new IOException("Cannot open Bluetooth connection.");
    }
    @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state) {
        if(status!=BluetoothGatt.GATT_SUCCESS) { fail("Bluetooth connection failed ("+status+"). Release the phone/Mac connection."); return; }
        if(state==BluetoothProfile.STATE_CONNECTED) {
            if(!g.requestMtu(185) && !g.discoverServices()) fail("Cannot discover printer services.");
        } else if(state==BluetoothProfile.STATE_DISCONNECTED) fail("Printer disconnected. Check paper before retrying.");
    }
    @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status) {
        if(status==BluetoothGatt.GATT_SUCCESS) packetSize=Math.max(20,Math.min(182,mtu-3));
        if(!g.discoverServices()) fail("Cannot discover printer services.");
    }
    @Override public void onServicesDiscovered(BluetoothGatt g,int status) {
        BluetoothGattService service=g.getService(SERVICE);
        characteristic=service==null?null:service.getCharacteristic(WRITE);
        if(status!=BluetoothGatt.GATT_SUCCESS || characteristic==null || (characteristic.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE)==0) {
            fail("Wrong or unsupported printer: receipt endpoint 18F0/2AF1 missing."); return;
        }
        ready.countDown();
    }
    @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status) {
        if(!WRITE.equals(c.getUuid())) return;
        writeStatus=status;
        CountDownLatch latch=writeDone; if(latch!=null) latch.countDown();
    }
    void fail(String message) { failure=message; ready.countDown(); CountDownLatch latch=writeDone; if(latch!=null) latch.countDown(); }
    void check() throws IOException {
        if(cancelled.get()) throw new IOException("Cancelled. Check paper before retrying.");
        if(failure!=null) throw new IOException(failure);
    }
    void send(byte[] bytes) throws Exception {
        if(!ready.await(20,TimeUnit.SECONDS)) throw new IOException("Printer connection timed out. Check power and other devices.");
        check();
        for(int offset=0;offset<bytes.length;) {
            check();
            int count=Math.min(packetSize,bytes.length-offset);
            boolean bulk=offset+count<bytes.length && (characteristic.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0;
            int type=bulk?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            byte[] chunk=Arrays.copyOfRange(bytes,offset,offset+count);
            writeDone=new CountDownLatch(1); writeStatus=-1;
            int result;
            if(Build.VERSION.SDK_INT>=33) result=gatt.writeCharacteristic(characteristic,chunk,type);
            else {
                characteristic.setWriteType(type); characteristic.setValue(chunk);
                result=gatt.writeCharacteristic(characteristic)?BluetoothStatusCodes.SUCCESS:-1;
            }
            if(result!=BluetoothStatusCodes.SUCCESS) throw new IOException("Bluetooth send rejected ("+result+"). Check paper before retrying.");
            if(!writeDone.await(10,TimeUnit.SECONDS)) throw new IOException("Bluetooth transfer stalled. Check paper before retrying.");
            check();
            if(writeStatus!=BluetoothGatt.GATT_SUCCESS) throw new IOException("Bluetooth write failed ("+writeStatus+"). Check paper before retrying.");
            offset+=count;
            // shortcut: conservative 8 KB/s pace protects this printer's receive buffer; raise only after full-bill tests.
            if(bulk) Thread.sleep(Math.max(5,(count*1000L+7999)/8000));
        }
    }
    @Override public void close() { if(gatt!=null) { gatt.disconnect(); gatt.close(); gatt=null; } }
}
