package ayala.tidytivi.companion;
import java.io.*;

/** Only app-owned scratch files are eligible; never delete native user backups. */
final class StorageCleanup {
 static void beforeUpdate(File cache,File files,File root)throws IOException{
  File[] entries=cache.listFiles();
  if(entries!=null)for(File f:entries)if(f.getName().equals("update.zip")||f.getName().startsWith("merge-"))remove(f);
  entries=files.listFiles();
  if(entries!=null)for(File f:entries)if(f.getName().equals("update.zip")||f.getName().startsWith("merge-"))remove(f);
  entries=root.listFiles();
  if(entries!=null)for(File f:entries)if(f.getName().startsWith(".incoming-")||f.getName().startsWith(".merged-")||f.getName().equals(".pending-curation"))remove(f);
  remove(new File(files,"backup.pending"));
  // An interrupted activation may still need its rollback files. Keep those
  // unless both installed halves exist. The user's native backup is separate.
  if(new File(root,"current/manifest.json").isFile()&&new File(files,"tidytivi.tmb").isFile()){
   remove(new File(root,"previous"));remove(new File(files,"backup.previous"));
  }
  remove(new File(files,"receiver-before-update.tmb"));
 }
 static void quietRemove(File f){try{remove(f);}catch(IOException ignored){}}
 static void remove(File f)throws IOException{
  if(!f.exists())return;
  if(f.isDirectory()){File[] all=f.listFiles();if(all==null)throw new IOException("Cannot read temporary directory.");for(File c:all)remove(c);}
  if(!f.delete())throw new IOException("Cannot remove temporary file.");
 }
}
