package ayala.tidytivi.companion;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.util.*;

/** Runs inside the receiver merge transaction; source is the attached incoming database. */
final class VodMerge {
 private static long scalar(SQLiteDatabase db,String sql,String... args){try(Cursor c=db.rawQuery(sql,args)){if(!c.moveToFirst())return 0;return c.getLong(0);}}
 private static List<String> columns(SQLiteDatabase db,String table){List<String> result=new ArrayList<>();try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){while(c.moveToNext())if(!"id".equals(c.getString(1)))result.add(c.getString(1));}return result;}
 private static String join(List<String> values){return android.text.TextUtils.join(",",values);}
 private static void catalog(SQLiteDatabase db,String table,long source,long receiver,String[] metadata,String categories)throws IOException{
  db.execSQL("CREATE INDEX main.tidytivi_merge_"+table+" ON "+table+"(playlist_id,xc_id)");
  db.execSQL("CREATE INDEX incoming_vod.tidytivi_merge_"+table+" ON "+table+"(playlist_id,xc_id)");
  String from="incoming_vod."+table,match="s.playlist_id="+source+" AND s.xc_id="+table+".xc_id";
  if(scalar(db,"SELECT count(*) FROM (SELECT xc_id FROM "+from+" WHERE playlist_id="+source+" GROUP BY xc_id HAVING xc_id IS NULL OR count(*)>1)")>0||scalar(db,"SELECT count(*) FROM (SELECT xc_id FROM "+table+" WHERE playlist_id="+receiver+" GROUP BY xc_id HAVING xc_id IS NOT NULL AND count(*)>1)")>0)throw new IOException("Ambiguous VOD identities. No changes restored.");
  List<String> updates=new ArrayList<>();for(String field:metadata)updates.add(field+"=(SELECT s."+field+" FROM "+from+" s WHERE "+match+")");
  if(categories!=null)updates.add("category_id=(SELECT target.id FROM "+from+" s JOIN incoming_vod."+categories+" source ON source.id=s.category_id JOIN "+categories+" target ON target.xc_id=source.xc_id AND target.playlist_id="+receiver+" WHERE "+match+")");
  updates.add("deleted_time=NULL");
  db.execSQL("UPDATE "+table+" SET "+join(updates)+" WHERE playlist_id="+receiver+" AND EXISTS (SELECT 1 FROM "+from+" s WHERE "+match+")");
  List<String> names=columns(db,table),values=new ArrayList<>();for(String col:names){if(col.equals("playlist_id"))values.add(""+receiver);else if(col.equals("category_id")&&categories!=null)values.add("(SELECT target.id FROM incoming_vod."+categories+" source JOIN "+categories+" target ON target.xc_id=source.xc_id AND target.playlist_id="+receiver+" WHERE source.id=s.category_id)");else values.add("s."+col);}
  db.execSQL("INSERT INTO "+table+" ("+join(names)+") SELECT "+join(values)+" FROM "+from+" s WHERE s.playlist_id="+source+" AND NOT EXISTS(SELECT 1 FROM "+table+" target WHERE target.playlist_id="+receiver+" AND target.xc_id=s.xc_id)");
  db.execSQL("UPDATE "+table+" SET deleted_time=? WHERE playlist_id="+receiver+" AND deleted_time IS NULL AND NOT EXISTS(SELECT 1 FROM "+from+" s WHERE "+match+")",new Object[]{System.currentTimeMillis()});
  db.execSQL("DROP INDEX main.tidytivi_merge_"+table);db.execSQL("DROP INDEX incoming_vod.tidytivi_merge_"+table);
 }
 static void apply(SQLiteDatabase db)throws IOException{
  try(Cursor c=db.rawQuery("SELECT id,name,url FROM incoming_vod.playlists WHERE include_vod=1 AND url LIKE 'xc:%'",null)){
   while(c.moveToNext()){
    long source=c.getLong(0);String name=c.getString(1),url=c.getString(2);List<Long> targets=new ArrayList<>();
    try(Cursor target=db.rawQuery("SELECT id FROM playlists WHERE include_vod=1 AND url=?",new String[]{url})){while(target.moveToNext())targets.add(target.getLong(0));}
    if(targets.size()==1)continue; // Same account keeps its native, independently updating catalogue.
    if(targets.isEmpty())try(Cursor target=db.rawQuery("SELECT id FROM playlists WHERE include_vod=1 AND name=? AND url LIKE 'xc:%'",new String[]{name})){while(target.moveToNext())targets.add(target.getLong(0));}
    if(targets.size()!=1)throw new IOException("The exported VOD provider cannot be matched to one existing connection. No changes restored.");
    long receiver=targets.get(0);
    // Provider XC IDs are the native identity. Recipient account validation is performed by Dispatcharr.
    catalog(db,"movie_categories",source,receiver,new String[]{"name","item_count","position_in_playlist","max_movie_added_time"},null);
    catalog(db,"series_categories",source,receiver,new String[]{"name","item_count","position_in_playlist","max_series_last_modified_time"},null);
    catalog(db,"movies",source,receiver,new String[]{"name","url","image","rating","added_time","drm_scheme","drm_license_url","position_in_category"},"movie_categories");
    catalog(db,"series",source,receiver,new String[]{"name","image","rating","last_modified_time","position_in_category"},"series_categories");
    db.execSQL("UPDATE playlists SET url=?,movie_count=(SELECT movie_count FROM incoming_vod.playlists WHERE id=?),series_count=(SELECT series_count FROM incoming_vod.playlists WHERE id=?) WHERE id=?",new Object[]{url,source,source,receiver});
   }
  }
 }
}
