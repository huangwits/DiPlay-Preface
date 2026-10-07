package com.shilapi.xcertplay.e01switch;

import android.app.*;
import android.os.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.*;

public class E01BluetoothSwitchActivity extends Activity {
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final List<Button> buttons=new ArrayList<Button>();
    TextView log; EditText port; boolean busy; String channel=""; int adbPort=5555;
    KeyPair key;
    static final String CHECK="id; getprop ro.build.display.id; getprop ro.product.model; getprop ro.board.platform; getprop ro.debuggable; getprop init.svc.gocsdk; getprop init.svc.mtkbt; ls -l /dev/stpbt /dev/goc_stpbt; ls -d /data/local/tmp/e01-btswitch-owned";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,16,24,12);root.setBackgroundColor(Color.rgb(246,248,251));
        TextView title=new TextView(this);title.setText("E01 蓝牙检查与切换");title.setTextSize(24);root.addView(title);
        TextView note=new TextView(this);note.setText("请停车后测试。切换会断开当前 CarPlay，原厂蓝牙电话和音乐会停用。首次可能需要允许调试授权。仅测试临时切换，重启后回到原厂启动流程。");note.setTextSize(16);root.addView(note);
        LinearLayout address=new LinearLayout(this);TextView label=new TextView(this);label.setText("本机 ADB 端口：");address.addView(label);
        port=new EditText(this);port.setInputType(2);port.setSingleLine(true);port.setText("5555");address.addView(port,new LinearLayout.LayoutParams(150,-2));root.addView(address);
        LinearLayout actions=new LinearLayout(this);root.addView(actions);
        add(actions,"检查连接",new Runnable(){public void run(){check();}});
        add(actions,"尝试切换",new Runnable(){public void run(){switchMode();}});
        add(actions,"恢复原厂",new Runnable(){public void run(){restoreMode();}});
        LinearLayout extras=new LinearLayout(this);root.addView(extras);
        Button settings=new Button(this);settings.setText("蓝牙设置");settings.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Exception e){say("无法打开蓝牙设置："+e.getMessage());}});extras.addView(settings);buttons.add(settings);
        Button copy=new Button(this);copy.setText("复制结果");copy.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("E01 蓝牙测试",log.getText()));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();});extras.addView(copy);
        ScrollView scroll=new ScrollView(this);log=new TextView(this);log.setTextSize(15);log.setTextIsSelectable(true);scroll.addView(log);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        say("已集成在 DiPlay 星瑞主包中。先点“检查连接”，再点“尝试切换”。\n本机 ADB 未开放时，应用会提示原因；不会绕过授权。\n若测试中断或退出，请重新打开并点“恢复原厂”；无法恢复时重启车机。");
    }
    void add(LinearLayout row,String text,Runnable action){Button b=new Button(this);b.setText(text);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));buttons.add(b);b.setOnClickListener(v->start(action));}
    void start(Runnable task){if(busy)return;try{adbPort=Integer.parseInt(port.getText().toString());if(adbPort<1||adbPort>65535)throw new Exception();}catch(Exception e){say("请输入有效端口");return;}
        busy=true;port.setEnabled(false);for(Button b:buttons)b.setEnabled(false);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker.execute(()->{try{task.run();}finally{runOnUiThread(()->{busy=false;port.setEnabled(true);for(Button b:buttons)b.setEnabled(true);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}});
    }
    void say(final String s){runOnUiThread(()->{log.append(s+"\n\n");try{Adb.writeFile(new File(getFilesDir(),"last-result.txt"),log.getText().toString().getBytes("UTF-8"));}catch(Exception ignored){}});}
    KeyPair key()throws Exception{if(key==null)key=Adb.keys(getFilesDir());return key;}
    String adb(String service,boolean ask)throws Exception{try(Adb a=new Adb(key(),adbPort,ask)){return a.service(service);}}
    String process(String executable,String command)throws Exception {
        final java.lang.Process p=new ProcessBuilder(executable,"-c",command).redirectErrorStream(true).start();
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try{byte[] b=new byte[1024];int n;while((n=p.getInputStream().read(b))!=-1){synchronized(output){if(output.size()<131072)output.write(b,0,n);}}}catch(Exception ignored){}});reader.setDaemon(true);reader.start();
        long end=System.nanoTime()+45000000000L;boolean done=false;
        while(System.nanoTime()<end){try{p.exitValue();done=true;break;}catch(IllegalThreadStateException e){Thread.sleep(100);}}
        if(!done){p.destroy();throw new IOException("命令超时；如已切换，请点恢复原厂");}reader.join(1000);synchronized(output){return output.toString("UTF-8").trim();}
    }
    void check(){say("正在检查本机 ADB；若出现授权窗口，请点允许……");try{say(adb("shell:"+CHECK,true));say("ADB 已连接。下一步可点“尝试切换”。");}catch(Exception e){say("本机 ADB 未连接："+e.getMessage()+"\n如果甲壳虫使用其他端口，请填同一端口。仅 USB 调试开启，并不保证本机网络 ADB 可用。");try{say("普通应用读取的状态（不代表管理员权限）：\n"+process("/system/bin/sh",CHECK));}catch(Exception x){say(x.toString());}}}
    boolean admin(){channel="";say("检查管理员通道……");try{if(process("su","id -u").trim().equals("0")){channel="su";say("已取得 root 执行通道");return true;}}catch(Exception ignored){}
        try{
            String uid=adb("shell:id -u",true).trim();
            if(!uid.equals("0")){say("请求 ADB 管理员模式；调试连接可能短暂断开……");try{say(adb("root:",false));}catch(IOException e){say("等待 ADB 重连……");}
                for(int i=0;i<5;i++){Thread.sleep(1000);try{uid=adb("shell:id -u",false).trim();if(uid.equals("0"))break;}catch(Exception ignored){}}
            }
            if(uid.equals("0")){channel="adb";say("已确认 ADB uid=0");return true;}
            say("车机未提供管理员权限（uid="+uid+"）。没有执行服务切换。");
        }catch(Exception e){say("无法取得执行权限："+e.getMessage()+"\n请确认本机网络 ADB 已开启并允许授权。");}return false;
    }
    String script(String name)throws Exception {try(InputStream i=getAssets().open("e01-bluetooth/"+name);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[2048];int n;while((n=i.read(b))!=-1)o.write(b,0,n);String s=o.toString("UTF-8");return channel.equals("su")?process("su",s):adb("shell:"+s,false);}}
    boolean off()throws Exception{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null)return true;if(a.getState()==BluetoothAdapter.STATE_OFF)return true;a.disable();for(int i=0;i<10;i++){Thread.sleep(500);if(a.getState()==BluetoothAdapter.STATE_OFF)return true;}return false;}
    boolean prepareSession(){try{if(com.shilapi.xcertplay.E01BluetoothSwitchIntegration.prepare())return true;say("CarPlay 未能及时断开，未切换蓝牙，请稍后重试。");}catch(Exception e){say("断开 CarPlay 失败："+e.getMessage());}return false;}
    void switchMode(){if(!admin())return;if(!prepareSession())return;boolean attempted=false;
        try{
            if(!off()){say("标准蓝牙未能关闭，已停止测试；未切换设备控制权。");return;}
            attempted=true;String result=script("switch.sh");say(result);
            if(!result.contains("E01_SWITCH_READY")){say("切换条件未满足或操作失败，请检查以上结果。");return;}
            BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null)throw new IOException("没有标准蓝牙适配器");a.enable();
            for(int i=0;i<20;i++){Thread.sleep(500);if(a.getState()==BluetoothAdapter.STATE_ON){com.shilapi.xcertplay.E01BluetoothSwitchIntegration.useSystem(this);say("标准蓝牙已报告开启。请打开蓝牙设置测试搜索和配对；尚未验证通话、音乐功能。测试完可点“恢复原厂”。");return;}}
            throw new IOException("标准蓝牙未在 10 秒内开启");
        }catch(Exception e){say("切换未完成："+e.getMessage());if(attempted){say("正在尝试恢复原厂……");restoreWithChannel();}}
    }
    void restoreMode(){if(admin()){if(!prepareSession())return;restoreWithChannel();}}
    void restoreWithChannel(){try{if(!off()){say("标准蓝牙未能关闭，为避免两个蓝牙栈同时使用设备，请重启车机恢复。");return;}String r=script("restore.sh");say(r);if(r.contains("E01_RESTORE_READY"))say("原厂服务和节点已恢复，请在原厂界面确认电话和音乐功能。");}catch(Exception e){say("恢复未确认："+e.getMessage()+"。请重启车机。");}}
    @Override public void onBackPressed(){if(busy){Toast.makeText(this,"操作中，请等待结果",Toast.LENGTH_SHORT).show();return;}super.onBackPressed();}
    @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
}
