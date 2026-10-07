package com.shilapi.xcertplay.e01switch;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;

/** Minimal, single-stream legacy ADB client. Only connects to this device. */
final class Adb implements Closeable {
    static final int CNXN=0x4e584e43, AUTH=0x48545541, OPEN=0x4e45504f,
        OKAY=0x59414b4f, WRTE=0x45545257, CLSE=0x45534c43;
    final Socket socket = new Socket();
    InputStream in; OutputStream out;
    static byte[] bytes(String s) throws Exception { return s.getBytes("UTF-8"); }
    static byte[] readFile(File f) throws Exception {
        try(InputStream i=new FileInputStream(f); ByteArrayOutputStream o=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096]; int n; while((n=i.read(b))!=-1)o.write(b,0,n); return o.toByteArray();
        }
    }
    static void writeFile(File f,byte[] b)throws Exception {try(FileOutputStream o=new FileOutputStream(f)){o.write(b);}}
    static KeyPair keys(File dir)throws Exception {
        File a=new File(dir,"adb.private"), b=new File(dir,"adb.public");
        if(a.exists()&&b.exists()) {KeyFactory k=KeyFactory.getInstance("RSA"); return new KeyPair(k.generatePublic(new X509EncodedKeySpec(readFile(b))),k.generatePrivate(new PKCS8EncodedKeySpec(readFile(a))));}
        KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair k=g.generateKeyPair();
        writeFile(a,k.getPrivate().getEncoded());writeFile(b,k.getPublic().getEncoded());return k;
    }
    static byte[] little(BigInteger n) {byte[] a=n.toByteArray(),b=new byte[256];for(int j=0;j<256&&j<a.length;j++)b[j]=a[a.length-1-j];return b;}
    static byte[] publicBlob(KeyPair k) {
        RSAPublicKey r=(RSAPublicKey)k.getPublic();BigInteger n=r.getModulus(),two=BigInteger.ONE.shiftLeft(32);
        return ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN).putInt(64).putInt(two.subtract(n.mod(two).modInverse(two)).intValue()).put(little(n)).put(little(BigInteger.ONE.shiftLeft(4096).mod(n))).putInt(r.getPublicExponent().intValue()).array();
    }
    Adb(KeyPair key,int port,boolean ask)throws Exception {
        try {
            socket.connect(new InetSocketAddress("127.0.0.1",port),2000);socket.setSoTimeout(ask?30000:5000);
            in=socket.getInputStream();out=socket.getOutputStream();send(CNXN,0x01000000,4096,bytes("host::\0"));
            Packet p=read();
            if(p.c==AUTH&&p.a==1) {
                if(p.b.length!=20)throw new IOException("ADB token 长度异常");
                Signature s=Signature.getInstance("NONEwithRSA");s.initSign(key.getPrivate());
                s.update(new byte[]{0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0,0x04,0x14});s.update(p.b);
                send(AUTH,2,0,s.sign());p=read();
                if(p.c==AUTH&&ask){send(AUTH,3,0,bytes(android.util.Base64.encodeToString(publicBlob(key),android.util.Base64.NO_WRAP)+" e01-switch@headunit\0"));p=read();}
            }
            if(p.c!=CNXN)throw new IOException("ADB 未授权；请允许车机弹出的调试授权后重试");
            socket.setSoTimeout(45000);
        }catch(Exception e){close();throw e;}
    }
    static class Packet {int c,a,d;byte[] b;}
    void exact(byte[] b)throws Exception {int off=0,n;while(off<b.length){n=in.read(b,off,b.length-off);if(n<0)throw new EOFException("ADB 连接已断开");off+=n;}}
    Packet read()throws Exception {
        byte[] h=new byte[24];exact(h);ByteBuffer v=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Packet p=new Packet();p.c=v.getInt();p.a=v.getInt();p.d=v.getInt();int len=v.getInt(),sum=v.getInt(),magic=v.getInt();
        if(len<0||len>4096||magic!=(p.c^0xffffffff))throw new IOException("ADB 包头无效");p.b=new byte[len];exact(p.b);int total=0;for(byte b:p.b)total+=b&255;
        if(total!=sum)throw new IOException("ADB 校验失败");return p;
    }
    void send(int c,int a,int d,byte[] b)throws Exception {int sum=0;for(byte x:b)sum+=x&255;out.write(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(c).putInt(a).putInt(d).putInt(b.length).putInt(sum).putInt(c^0xffffffff).array());out.write(b);out.flush();}
    String service(String name)throws Exception {
        byte[] request=bytes(name+"\0");if(request.length>4096)throw new IOException("命令太长");
        send(OPEN,1,0,request);ByteArrayOutputStream result=new ByteArrayOutputStream();long deadline=System.nanoTime()+60000000000L;
        while(System.nanoTime()<deadline){Packet p=read();if(p.d!=1)throw new IOException("ADB stream 无效");
            if(p.c==WRTE){if(result.size()+p.b.length>131072)throw new IOException("输出过长");result.write(p.b);send(OKAY,1,p.a,new byte[0]);}
            else if(p.c==CLSE){send(CLSE,1,p.a,new byte[0]);return result.toString("UTF-8").trim();}
            else if(p.c!=OKAY)throw new IOException("ADB 响应异常");
        }throw new IOException("ADB 操作超时");
    }
    public void close(){try{socket.close();}catch(Exception ignored){}}
}
