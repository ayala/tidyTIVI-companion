package ayala.tidytivi.companion;
import android.app.*;import android.os.*;import android.content.*;import android.content.pm.PackageManager;import android.net.Uri;import android.provider.Settings;import android.media.MediaScannerConnection;import android.view.*;import android.widget.*;import android.graphics.Color;
import org.json.*;import java.io.*;import java.net.*;import java.security.*;import java.util.*;import java.util.concurrent.*;import java.util.zip.*;

public class MainActivity extends Activity {
 private TextView status; private Button update,setup; private boolean busy=false;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private File root(){return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"tidyTIVI");}
 private LinearLayout home; private PairingServer pairing; private boolean connecting;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final Runnable expire=()->{if(connecting){stopPairing();home();show("Connection expired. Press Connect to try again.");}};
 private static final int BLUE=Color.rgb(7,147,215);
 private Button button(String text){Button b=new Button(this);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(16);styleButton(b,BLUE);return b;}
 private void styleButton(Button b,int color){
  android.graphics.drawable.GradientDrawable normal=new android.graphics.drawable.GradientDrawable();normal.setColor(color);normal.setCornerRadius(dp(5));
  android.graphics.drawable.GradientDrawable focus=new android.graphics.drawable.GradientDrawable();focus.setColor(color);focus.setCornerRadius(dp(5));focus.setStroke(dp(3),Color.WHITE);
  android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},focus);background.addState(new int[]{android.R.attr.state_pressed},focus);background.addState(new int[]{},normal);b.setBackground(background);
 }
 private void addButton(LinearLayout layout,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(320),dp(48));p.topMargin=dp(10);layout.addView(b,p);}
 private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
 public void onCreate(Bundle state){super.onCreate(state);
  home=new LinearLayout(this);home.setOrientation(1);home.setGravity(Gravity.CENTER);home.setPadding(dp(32),dp(14),dp(32),dp(14));home.setBackgroundColor(Color.rgb(15,22,32));
  ImageView logo=new ImageView(this);logo.setImageResource(ayala.tidytivi.companion.R.drawable.logo);
  logo.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,android.graphics.Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(16));}});logo.setClipToOutline(true);home.addView(logo,new LinearLayout.LayoutParams(dp(100),dp(100)));
  TextView title=label("Your TiviMate setup, up to date",26);title.setPadding(0,dp(15),0,dp(6));home.addView(title);
  setup=button("Connect");addButton(home,setup);setup.setOnClickListener(v->configure());
  update=button("Update TiviMate");addButton(home,update);update.setOnClickListener(v->begin());
  status=label("",17);status.setTextColor(Color.LTGRAY);status.setPadding(0,dp(18),0,0);home.addView(status,new LinearLayout.LayoutParams(-1,-2));
  home();show(getPreferences(0).getString("url","").isEmpty()?"Press Connect to get started.":"Link saved. Press Update TiviMate.");
 }
 private void home(){connecting=false;boolean linked=!getPreferences(0).getString("url","").isEmpty();setup.setText(linked?"Connected":"Connect");styleButton(setup,linked?Color.rgb(35,139,77):BLUE);setContentView(home);setup.setFocusableInTouchMode(true);setup.requestFocus();}
 private void show(String message){runOnUiThread(()->status.setText(message));}
 private void stopPairing(){ui.removeCallbacks(expire);if(pairing!=null){pairing.close();pairing=null;}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
 @Override public void onBackPressed(){if(connecting){stopPairing();home();}else super.onBackPressed();}
 @Override protected void onStop(){if(connecting){stopPairing();home();}super.onStop();}
 @Override protected void onDestroy(){stopPairing();worker.shutdown();super.onDestroy();}
 private InetAddress localAddress()throws Exception{
  ArrayList<InetAddress> candidates=new ArrayList<>();Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
  while(interfaces.hasMoreElements()){NetworkInterface n=interfaces.nextElement();if(!n.isUp()||n.isLoopback())continue;Enumeration<InetAddress> addresses=n.getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(a instanceof Inet4Address&&a.isSiteLocalAddress()){if(n.getName().startsWith("wlan")||n.getName().startsWith("eth"))return a;candidates.add(a);}}}
  if(candidates.isEmpty())throw new IOException();return candidates.get(0);
 }
 private void configure(){if(busy)return;stopPairing();connecting=true;
  LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setGravity(Gravity.CENTER);content.setPadding(dp(20),dp(12),dp(20),dp(12));content.setBackgroundColor(Color.rgb(15,22,32));
  TextView heading=label("Scan to connect",25);content.addView(heading);
  TextView hint=label("Phone + Firestick on the same Wi-Fi",14);hint.setPadding(0,dp(10),0,dp(5));
  try{String page;try(InputStream in=getAssets().open("pairing.html");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);page=out.toString("UTF-8");}
   pairing=new PairingServer(localAddress(),page,new PairingServer.Receiver(){public void save(String value)throws Exception{String url=normalize(value);if(!url.startsWith("https://"))throw new IOException();if(!getPreferences(0).edit().putString("url",url).commit())throw new IOException();}public void complete(){runOnUiThread(()->{final PairingServer completed=pairing;ui.removeCallbacks(expire);home();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);show("Link saved. Press Update TiviMate.");ui.postDelayed(()->{if(pairing==completed)stopPairing();},20000);});}});
   com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(pairing.url(),com.google.zxing.BarcodeFormat.QR_CODE,640,640);
   android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(640,640,android.graphics.Bitmap.Config.RGB_565);int[] pixels=new int[640*640];for(int y=0;y<640;y++)for(int x=0;x<640;x++)pixels[y*640+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;bitmap.setPixels(pixels,0,640,0,0,640,640);
   ImageView qr=new ImageView(this);qr.setImageBitmap(bitmap);qr.setContentDescription("Scan this QR code with your phone to connect");LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(dp(235),dp(235));qp.topMargin=dp(12);content.addView(qr,qp);pairing.start();ui.postDelayed(expire,10*60*1000);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }catch(Exception e){stopPairing();hint.setText("Connect to Wi-Fi, or enter your link manually.");}
  content.addView(hint);Button manual=button("Enter link manually");addButton(content,manual);manual.setOnClickListener(v->manualLink());
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);setContentView(scroll);manual.requestFocus();
 }
 private void manualLink(){EditText entry=new EditText(this);entry.setSingleLine(true);entry.setInputType(0x81);entry.setHint("HTTPS download link");entry.setText(getPreferences(0).getString("url",""));
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Download link").setView(entry).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String url=normalize(entry.getText().toString().trim());getPreferences(0).edit().putString("url",url).apply();dialog.dismiss();stopPairing();home();show("Link saved. Press Update TiviMate.");}catch(Exception e){entry.setError("Enter a valid HTTPS download link.");}}));dialog.show();
 }
 static String normalize(String value)throws Exception{
  Uri uri=Uri.parse(value);String host=uri.getHost();
  if(host==null || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && (host.equals("10.0.2.2") || host.equals("127.0.0.1")))))throw new IOException();
  if(uri.getUserInfo()!=null)throw new IOException();
  if(host.equals("www.dropbox.com") || host.equals("dropbox.com")){
   Uri.Builder b=uri.buildUpon().clearQuery();for(String key:uri.getQueryParameterNames())if(!key.equals("dl")&&!key.equals("raw"))for(String val:uri.getQueryParameters(key))b.appendQueryParameter(key,val);value=b.appendQueryParameter("dl","1").build().toString();
  }return DriveLinks.normalize(value);
 }
 private boolean permissions(){
  if(Build.VERSION.SDK_INT>=30 && !Environment.isExternalStorageManager()){
   show("Allow file access for tidyTIVI, return here, and press Update again.");
   try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}catch(Exception unavailable){show("Open the device app settings and allow file access for tidyTIVI, then try Update again.");}}return false;
  }
  if(Build.VERSION.SDK_INT<30 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},1);show("Allow file access, then press Update again.");return false;}
  return true;
 }
 private void begin(){if(busy)return;String url=getPreferences(0).getString("url","");if(url.isEmpty()){configure();return;}if(!permissions())return;
  try{getPackageManager().getPackageInfo("ar.tvplayer.tv",0);}catch(Exception e){show("Install and activate TiviMate first, then return here.");return;}
  busy=true;update.setEnabled(false);setup.setEnabled(false);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  worker.submit(()->{try{install(url);runOnUiThread(()->{show("Backup and logos installed. Confirm Restore in TiviMate.");handoff();});}catch(Exception e){show(e instanceof UserError?e.getMessage():"Update failed. Check the link, connection, free space and file permissions. Your previous installed bundle was kept where possible.");}
   finally{runOnUiThread(()->{busy=false;update.setEnabled(true);setup.setEnabled(true);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}});
 }
 static class UserError extends IOException{UserError(String text){super(text);}}
 private void install(String url)throws Exception{
  File base=root();if(!base.exists()&&!base.mkdirs())throw new UserError("Cannot create the download folder. Allow file access.");
  File stage=new File(base,".incoming-"+UUID.randomUUID());if(!stage.mkdir())throw new IOException();File zip=new File(getCacheDir(),"update.zip");
  try{show("Downloading the latest bundle…");download(url,zip);show("Verifying backup and logos…");extract(zip,stage);verify(stage);
   File pending=new File(getFilesDir(),"backup.pending");copy(new File(stage,"tidytivi.tmb"),pending);
   File current=new File(base,"current"),previous=new File(base,"previous");remove(previous);
   File backup=new File(getFilesDir(),"tidytivi.tmb"),oldBackup=new File(getFilesDir(),"backup.previous");remove(oldBackup);
   boolean hadCurrent=current.exists(),hadBackup=backup.exists();
   if(hadCurrent&&!current.renameTo(previous))throw new IOException();
   try{
    if(!stage.renameTo(current))throw new IOException();
    if(hadBackup&&!backup.renameTo(oldBackup))throw new IOException();
    if(!pending.renameTo(backup))throw new IOException();
   }catch(Exception failure){
    if(current.exists())remove(current);if(hadCurrent)previous.renameTo(current);
    if(oldBackup.exists()){if(backup.exists())backup.delete();oldBackup.renameTo(backup);}throw failure;
   }
   remove(oldBackup);
   ArrayList<String> paths=new ArrayList<>();images(new File(current,"logos"),paths);
   if(!paths.isEmpty()){CountDownLatch scanned=new CountDownLatch(paths.size());MediaScannerConnection.scanFile(this,paths.toArray(new String[0]),null,(p,u)->scanned.countDown());scanned.await(45,TimeUnit.SECONDS);}
   remove(previous);show("Installed "+paths.size()+" logo files.");
  }finally{remove(stage);zip.delete();}
 }
 private void download(String input,File dest)throws Exception{
  String url=normalize(input);HttpURLConnection conn=null;java.net.CookieManager cookies=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  try{for(int redirect=0;redirect<8;redirect++){
    conn=(HttpURLConnection)new URL(url).openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(30000);conn.setReadTimeout(60000);conn.setRequestProperty("User-Agent","tidyTIVI/0.6.1");
    for(Map.Entry<String,List<String>> h:cookies.get(new URI(url),Collections.emptyMap()).entrySet())conn.setRequestProperty(h.getKey(),android.text.TextUtils.join("; ",h.getValue()));
    int code=conn.getResponseCode();cookies.put(new URI(url),conn.getHeaderFields());if(code>=300&&code<400){String next=conn.getHeaderField("Location");if(next==null)throw new IOException();String resolved=new URL(new URL(url),next).toString();conn.disconnect();url=normalize(resolved);continue;}
    if(code!=200)throw new UserError("Download unavailable. Check the shared link and cloud file access.");
    if(conn.getContentType()!=null && conn.getContentType().toLowerCase(Locale.US).contains("text/html")){
     if(!DriveLinks.host(new URL(url).getHost()))throw new UserError("The link returned a web page, not the update bundle.");
     ByteArrayOutputStream page=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()){byte[] part=new byte[8192];int n;while((n=in.read(part))!=-1){if(page.size()+n>1024*1024)throw new IOException();page.write(part,0,n);}}
     try{url=normalize(DriveLinks.confirmation(url,new String(page.toByteArray(),"UTF-8")));}catch(Exception e){throw new UserError("Google Drive did not allow the download. Check link sharing or try again later.");}conn.disconnect();continue;
    }
    try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(dest)){byte[] b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>512L*1024*1024)throw new UserError("Bundle exceeds the 512 MB download limit.");out.write(b,0,n);}}return;
   }throw new IOException();
  }finally{if(conn!=null)conn.disconnect();}
 }
 static void extract(File zip,File stage)throws Exception{
  String root=stage.getCanonicalPath()+File.separator;long total=0;int files=0;Set<String> seen=new HashSet<>();
  try(ZipInputStream in=new ZipInputStream(new FileInputStream(zip))){ZipEntry e;byte[] b=new byte[65536];while((e=in.getNextEntry())!=null){
   String name=e.getName();if(name.startsWith("/")||name.contains("\\")||!seen.add(name))throw new UserError("Invalid bundle paths.");File file=new File(stage,name);if(!file.getCanonicalPath().startsWith(root))throw new UserError("Invalid bundle paths.");
   if(++files>100000)throw new IOException();if(e.isDirectory()){if(!file.isDirectory()&&!file.mkdirs())throw new IOException();continue;}
   if(!file.getParentFile().isDirectory()&&!file.getParentFile().mkdirs())throw new IOException();
   try(OutputStream out=new FileOutputStream(file)){int n;while((n=in.read(b))!=-1){total+=n;if(total>1024L*1024*1024)throw new UserError("Extracted bundle exceeds 1 GB.");out.write(b,0,n);}}
  }}
 }
 static byte[] read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
 static void verify(File stage)throws Exception{
  File manifestFile=new File(stage,"manifest.json");if(!manifestFile.isFile()||manifestFile.length()>20*1024*1024)throw new UserError("This is not a tidyTIVI update bundle.");
  JSONObject manifest=new JSONObject(new String(read(manifestFile),"UTF-8"));
  if(!"tidytivi.bundle.v1".equals(manifest.getString("schema"))||!"tidytivi.tmb".equals(manifest.getString("backup"))||!"5.3.3".equals(manifest.getString("target_version"))||!"/sdcard/Download/tidyTIVI/current".equals(manifest.getString("receiver_root")))throw new UserError("Unsupported bundle format.");
  JSONObject files=manifest.getJSONObject("files");if(!files.has("tidytivi.tmb")||!files.has("lineup.m3u"))throw new UserError("Incomplete bundle.");
  Set<String> expected=new HashSet<>();expected.add("manifest.json");Iterator<String> keys=files.keys();String root=stage.getCanonicalPath()+File.separator;
  while(keys.hasNext()){String key=keys.next();File file=new File(stage,key);if(!file.getCanonicalPath().startsWith(root)||key.contains("\\"))throw new IOException();JSONObject info=files.getJSONObject(key);
   if(!file.isFile()||file.length()!=info.getLong("bytes")||!hash(file).equals(info.getString("sha256")))throw new UserError("Bundle verification failed. Re-export and upload it again.");expected.add(key);
  }
  Set<String> actual=new HashSet<>();list(stage,stage,actual);if(!actual.equals(expected))throw new UserError("Bundle contains unexpected files.");
 }
 static void list(File root,File dir,Set<String> files)throws Exception{File[] all=dir.listFiles();if(all==null)throw new IOException();for(File f:all)if(f.isDirectory())list(root,f,files);else files.add(root.toURI().relativize(f.toURI()).getPath());}
 static void images(File dir,List<String> paths){File[] all=dir.listFiles();if(all==null)return;for(File f:all)if(f.isDirectory())images(f,paths);else if(f.getName().matches("(?i).*\\.(png|jpg|jpeg|webp)$"))paths.add(f.getAbsolutePath());}
 static void copy(File a,File b)throws Exception{try(InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}}
 static void remove(File f)throws IOException{if(!f.exists())return;if(f.isDirectory()){File[] all=f.listFiles();if(all==null)throw new IOException();for(File c:all)remove(c);}if(!f.delete())throw new IOException();}
 private void handoff(){try{Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://ayala.tidytivi.companion.backups/tidytivi-"+UUID.randomUUID().toString().replace("-","")+".tmb"),"application/octet-stream").setPackage("ar.tvplayer.tv").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);}catch(Exception e){show("Bundle installed. Open TiviMate → Settings → General → Restore data and select Download/tidyTIVI/current/tidytivi.tmb.");}}
}
