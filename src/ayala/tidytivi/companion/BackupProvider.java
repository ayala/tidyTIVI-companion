package ayala.tidytivi.companion;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;
public class BackupProvider extends ContentProvider {
 public boolean onCreate(){return true;} private File file(Uri u)throws FileNotFoundException{if(u.getPath()==null||!u.getPath().matches("/tidytivi-[a-f0-9]{32}\\.tmb"))throw new FileNotFoundException();return new File(getContext().getFilesDir(),"tidytivi.tmb");}
 public String getType(Uri u){return "application/octet-stream";}
 public Cursor query(Uri u,String[] columns,String s,String[] a,String sort){try{File f=file(u);if(columns==null)columns=new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};MatrixCursor c=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=u.getLastPathSegment();else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=f.length();}c.addRow(row);return c;}catch(Exception e){return null;}}
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!mode.equals("r"))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
