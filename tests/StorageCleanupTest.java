package ayala.tidytivi.companion;
import java.io.*;
import java.nio.file.*;
public class StorageCleanupTest {
 static File put(File dir,String name)throws Exception{File f=new File(dir,name);f.getParentFile().mkdirs();Files.write(f.toPath(),new byte[]{1});return f;}
 public static void main(String[] args)throws Exception{
  File base=Files.createTempDirectory("tidytivi-storage-test").toFile();
  try{
   File cache=new File(base,"cache"),files=new File(base,"files"),root=new File(base,"shared/Download/tidyTIVI");
   File nativeBackup=put(base,"shared/TiviMate_backup_user.tmb"),current=put(root,"current/manifest.json"),installed=put(files,"tidytivi.tmb"),unrelated=put(cache,"unrelated.bin");
   File[] scratch={put(files,"merge-interrupted/large.db"),put(files,"update.zip"),put(cache,"merge-interrupted/receiver/TvPlayer.db"),put(cache,"update.zip"),put(root,".incoming-failed/large.db"),put(root,".merged-failed/large.db"),put(root,".pending-curation/large.db"),put(root,"previous/old.db"),put(files,"backup.previous"),put(files,"backup.pending"),put(files,"receiver-before-update.tmb")};
   StorageCleanup.beforeUpdate(cache,files,root);StorageCleanup.beforeUpdate(cache,files,root);
   for(File f:scratch)if(f.exists())throw new AssertionError("scratch retained: "+f);
   for(File f:new File[]{nativeBackup,current,installed,unrelated})if(!f.exists())throw new AssertionError("user/active data removed: "+f);
   installed.delete();File rollback=put(root,"previous/old.db"),oldBackup=put(files,"backup.previous");
   StorageCleanup.beforeUpdate(cache,files,root);if(!rollback.exists()||!oldBackup.exists())throw new AssertionError("interrupted activation rollback lost");
   System.out.println("PASS: interrupted scratch cleanup, repeated cleanup, active/native data protection, rollback protection");
  }finally{StorageCleanup.remove(base);}
 }
}
