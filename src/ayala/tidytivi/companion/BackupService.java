package ayala.tidytivi.companion;
import android.accessibilityservice.*;import android.view.accessibility.*;import android.os.*;import android.content.*;import java.io.*;
public class BackupService extends AccessibilityService {
 static BackupService instance;Handler h=new Handler();boolean active=false;long started,lastSize=-1;int stage=0,tries=0,stable=0;java.util.Set<String> prior=new java.util.HashSet<>();
 static boolean available(){return instance!=null;}
 static void request(){if(instance!=null)instance.begin();}
 public void onDestroy(){instance=null;active=false;h.removeCallbacksAndMessages(null);super.onDestroy();}
 void finish(String path,String error){active=false;getSharedPreferences("MainActivity",0).edit().putString("auto_backup_ready",path).putString("auto_backup_error",error).commit();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
 public void onServiceConnected(){instance=this;}public void onInterrupt(){if(active)finish("","Automatic backup interrupted. Your TiviMate setup was not restored.");}public void onAccessibilityEvent(AccessibilityEvent e){}
 void begin(){if(active)return;active=true;stage=0;tries=0;stable=0;lastSize=-1;started=System.currentTimeMillis();prior.clear();File[] files=Environment.getExternalStorageDirectory().listFiles();if(files!=null)for(File f:files)prior.add(f.getName());Intent i=getPackageManager().getLeanbackLaunchIntentForPackage("ar.tvplayer.tv");if(i==null){finish("","TiviMate could not be opened. No restore was performed.");return;}i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);h.postDelayed(this::tick,1200);}
 boolean has(AccessibilityNodeInfo n,String text){if(n==null)return false;if(text.contentEquals(n.getText()==null?"":n.getText()))return true;for(int i=0;i<n.getChildCount();i++)if(has(n.getChild(i),text))return true;return false;}
 boolean click(AccessibilityNodeInfo n,String text){if(n==null)return false;if(text.contentEquals(n.getText()==null?"":n.getText())){for(AccessibilityNodeInfo p=n;p!=null;p=p.getParent())if(p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}for(int i=0;i<n.getChildCount();i++)if(click(n.getChild(i),text))return true;return false;}
 boolean scroll(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scroll(n.getChild(i)))return true;return false;}
 void tick(){if(!active)return;if(System.currentTimeMillis()-started>120000){finish("","Automatic backup could not finish. TiviMate 5.3.3 with English menus is currently required. No restore was performed.");return;}AccessibilityNodeInfo n=getRootInActiveWindow();if(n!=null&&"ar.tvplayer.tv".contentEquals(n.getPackageName())){
 
 switch(stage){
 case 0:if(has(n,"Back up data")){stage=2;break;}if(has(n,"General")&&has(n,"Playlists")){stage=1;break;}if(click(n,"Settings")){stage=1;}else if(++tries<=5){performGlobalAction(GLOBAL_ACTION_BACK);}break;
 case 1:if(click(n,"General")){stage=2;}break;
 case 2:if(click(n,"Back up data")){stage=3;}else{scroll(n);}break;
 case 3:if(click(n,"Internal shared storage")){stage=4;}break;
 case 4:if(has(n,"..")&&click(n,"Save")){stage=5;}break;
 case 5:File[] fs=Environment.getExternalStorageDirectory().listFiles();if(fs!=null)for(File f:fs)if(f.getName().startsWith("TiviMate_backup_")&&f.getName().endsWith(".tmb")&&!prior.contains(f.getName())&&f.lastModified()>=started&&f.length()>100){long size=f.length();stable=size==lastSize?stable+1:0;lastSize=size;if(stable>=3){finish(f.getAbsolutePath(),"");return;}}break;
 }
 }h.postDelayed(this::tick,1200);}
}