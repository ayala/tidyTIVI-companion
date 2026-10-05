package ayala.tidytivi.companion;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Changes only a disposable receiver database extracted from its fresh backup. */
final class CurationMerge {
 private static final Pattern PROFILE=Pattern.compile("(?:file://)?/sdcard/Download/tidyTIVI/current/(lineup(?:-\\d+)?\\.m3u)");
 private static final String[] CURATION={"name","custom_name","url","logo","tvg_id","tvg_name","tvg_shift","tvg_ch_no","catchup_type","catchup_hours","catchup_source","position_in_playlist","position_in_group","user_tvg_id"};
 private static List<ContentValues> rows(SQLiteDatabase db,String sql,Object... args){
  String[] a=new String[args.length];for(int i=0;i<a.length;i++)a[i]=String.valueOf(args[i]);List<ContentValues> result=new ArrayList<>();
  try(Cursor c=db.rawQuery(sql,a)){while(c.moveToNext()){ContentValues v=new ContentValues();for(int i=0;i<c.getColumnCount();i++){String n=c.getColumnName(i);switch(c.getType(i)){case Cursor.FIELD_TYPE_NULL:v.putNull(n);break;case Cursor.FIELD_TYPE_INTEGER:v.put(n,c.getLong(i));break;case Cursor.FIELD_TYPE_FLOAT:v.put(n,c.getDouble(i));break;case Cursor.FIELD_TYPE_BLOB:v.put(n,c.getBlob(i));break;default:v.put(n,c.getString(i));}}result.add(v);}}
  return result;
 }
 private static void set(ContentValues v,String k,Object value){if(value==null)v.putNull(k);else if(value instanceof Long)v.put(k,(Long)value);else if(value instanceof Integer)v.put(k,(Integer)value);else if(value instanceof Double)v.put(k,(Double)value);else v.put(k,String.valueOf(value));}
 private static ContentValues fields(ContentValues row,String... names){ContentValues v=new ContentValues();for(String n:names)set(v,n,row.get(n));return v;}
 private static String canonical(String name){return name.substring(name.lastIndexOf(':')+1).trim().toLowerCase(Locale.ROOT);}
 private static long id(ContentValues row){return row.getAsLong("id");}
 private static ContentValues copy(ContentValues row){ContentValues v=new ContentValues(row);v.remove("id");return v;}
 private static void update(SQLiteDatabase db,String table,long id,ContentValues values){db.update(table,values,"id=?",new String[]{String.valueOf(id)});}
 private static Map<String,ContentValues> playlists(SQLiteDatabase db)throws IOException{
  Map<String,ContentValues> result=new LinkedHashMap<>();for(ContentValues row:rows(db,"SELECT * FROM playlists")){Matcher m=PROFILE.matcher(row.getAsString("url"));if(m.matches()&&result.put(m.group(1),row)!=null)throw new IOException("Duplicate profile playlists. Update cancelled.");}return result;
 }
 private static Map<String,ContentValues> channels(SQLiteDatabase db,long pid)throws IOException{
  Map<String,ContentValues> result=new LinkedHashMap<>();for(ContentValues row:rows(db,"SELECT * FROM channels WHERE playlist_id=?",pid)){if(result.put(row.getAsString("name"),row)!=null)throw new IOException("Ambiguous channel identities. Update cancelled.");}return result;
 }
 private static Set<String> members(SQLiteDatabase db,long gid){Set<String>s=new HashSet<>();for(ContentValues row:rows(db,"SELECT c.name FROM channels c JOIN channel_group_links l ON l.channel_id=c.id WHERE l.group_id=?",gid))s.add(row.getAsString("name"));return s;}
 private static Long source(SQLiteDatabase db,SQLiteDatabase src,Long sid,Map<Long,Long> map)throws IOException{
  if(sid==null)return null;if(map.containsKey(sid))return map.get(sid);
  List<ContentValues> found=rows(src,"SELECT * FROM tvg_sources WHERE id=?",sid);if(found.size()!=1)throw new IOException("Missing EPG mapping.");ContentValues row=found.get(0);
  List<ContentValues> old=rows(db,"SELECT id FROM tvg_sources WHERE url=? AND type=?",row.getAsString("url"),row.getAsLong("type"));if(old.size()>1)throw new IOException("Ambiguous EPG mapping.");long value;
  if(old.isEmpty()){ContentValues v=copy(row);v.putNull("playlist_id");value=db.insertOrThrow("tvg_sources",null,v);}else value=id(old.get(0));map.put(sid,value);return value;
 }
 private static Map<Long,String> identities(JSONObject manifest,String playlist,SQLiteDatabase db,long pid)throws Exception{
  Map<Long,String> result=new HashMap<>();Set<String> keys=new HashSet<>();JSONArray entries=manifest.optJSONArray("group_mappings");if(entries==null)return result;
  for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(!playlist.equals(e.getString("playlist")))continue;long gid=e.getLong("id");String key=e.getString("key");
   List<ContentValues> found=rows(db,"SELECT * FROM channel_groups WHERE id=? AND playlist_id=?",gid,pid);
   if(found.size()!=1||!e.optString("name","").equals(found.get(0).getAsString("name")))continue;
   if(result.put(gid,key)!=null||!keys.add(key))throw new IOException("Conflicting saved group identities. Update cancelled.");
  }return result;
 }
 static int merge(File receiver,File incoming,JSONObject oldManifest,JSONObject incomingManifest)throws Exception{
  if(receiver.getCanonicalFile().equals(incoming.getCanonicalFile()))throw new IOException("Separate merge inputs required.");
  SQLiteDatabase db=SQLiteDatabase.openDatabase(receiver.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE);
  SQLiteDatabase src=null;
  try{
   src=SQLiteDatabase.openDatabase(incoming.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
   if(db.getVersion()!=60||src.getVersion()!=60)throw new IOException("Only TiviMate 5.3.3 backups are supported for this update.");
   Map<String,ContentValues> incomingProfiles=playlists(src),existingProfiles=playlists(db);
   if(incomingProfiles.isEmpty()||existingProfiles.isEmpty())throw new IOException("This backup must contain the existing tidyTIVI profile playlists. No changes were restored.");
   db.execSQL("ATTACH DATABASE ? AS incoming_vod",new Object[]{incoming.getAbsolutePath()});
   JSONArray nextMappings=new JSONArray();JSONArray previousMappings=oldManifest.optJSONArray("group_mappings");if(previousMappings!=null)for(int n=0;n<previousMappings.length();n++){JSONObject entry=previousMappings.getJSONObject(n);if(!incomingProfiles.containsKey(entry.getString("playlist")))nextMappings.put(entry);}
   db.setForeignKeyConstraintsEnabled(true);db.beginTransactionNonExclusive();int count=0;Map<Long,Long> sources=new HashMap<>();
   try{
    for(String key:incomingProfiles.keySet()){
     ContentValues ip=incomingProfiles.get(key),rp=existingProfiles.get(key);
     if(rp==null){ContentValues added=copy(ip);long addedId=db.insertOrThrow("playlists",null,added);rp=new ContentValues(added);rp.put("id",addedId);existingProfiles.put(key,rp);}
     long iid=id(ip),pid=id(rp);
     Map<String,ContentValues> incomingChannels=channels(src,iid),oldChannels=channels(db,pid);
     for(String n:incomingChannels.keySet())if(!n.matches("tidytivi-\\d+"))throw new IOException("Unsupported channel identities.");
     List<ContentValues> oldGroups=rows(db,"SELECT * FROM channel_groups WHERE playlist_id=?",pid);Map<Long,Set<String>> oldMembers=new HashMap<>();for(ContentValues g:oldGroups)oldMembers.put(id(g),members(db,id(g)));
     Map<Long,String> oldIdentities=identities(oldManifest,key,db,pid),newIdentities=identities(incomingManifest,key,src,iid);Set<Long> managed=new HashSet<>(oldIdentities.keySet());
     for(ContentValues c:oldChannels.values()){Long gid=c.getAsLong("original_group_id");if(gid!=null)managed.add(gid);}
     Map<Long,Long> groups=new HashMap<>();Set<Long> used=new HashSet<>();Set<String> emitted=new HashSet<>();
     List<ContentValues> incomingGroups=rows(src,"SELECT * FROM channel_groups WHERE playlist_id=?",iid);Set<Long> reserved=new HashSet<>();for(ContentValues g:incomingGroups)for(ContentValues old:oldGroups)if(canonical(g.getAsString("name")).equals(canonical(old.getAsString("name"))))reserved.add(id(old));
     for(ContentValues g:incomingGroups){
      List<ContentValues> matches=new ArrayList<>();String stableKey=newIdentities.get(id(g));
      if(stableKey!=null)for(ContentValues old:oldGroups)if(stableKey.equals(oldIdentities.get(id(old))))matches.add(old);
      if(matches.isEmpty())for(ContentValues old:oldGroups)if(!used.contains(id(old))&&(stableKey==null||!oldIdentities.containsKey(id(old)))&&g.getAsString("name").equals(old.getAsString("name")))matches.add(old);
      if(matches.isEmpty())for(ContentValues old:oldGroups)if(!used.contains(id(old))&&(stableKey==null||!oldIdentities.containsKey(id(old)))&&canonical(g.getAsString("name")).equals(canonical(old.getAsString("name"))))matches.add(old);
      if(matches.isEmpty()){
       Set<String> names=members(src,id(g));int best=0;
       for(ContentValues old:oldGroups){if(used.contains(id(old))||reserved.contains(id(old))||(stableKey!=null&&oldIdentities.containsKey(id(old))))continue;Set<String> common=new HashSet<>(oldMembers.get(id(old)));common.retainAll(names);int score=common.size();if(score>best){best=score;matches.clear();}if(score>0&&score==best)matches.add(old);}
      }
      if(matches.size()>1)throw new IOException("A renamed group matches more than one existing group. Update cancelled to protect your settings.");
      long gid;
      if(!matches.isEmpty()){
       gid=id(matches.get(0));used.add(gid);update(db,"channel_groups",gid,fields(g,"name","position_in_playlist","deleted_time"));
       ContentValues v=new ContentValues();v.putNull("custom_group_name");db.update("channel_group_options",v,"playlist_id=? AND group_id=? AND type=4",new String[]{""+pid,""+gid});
      }else{
       ContentValues v=copy(g);v.put("playlist_id",pid);gid=db.insertOrThrow("channel_groups",null,v);
       for(ContentValues option:rows(src,"SELECT * FROM channel_group_options WHERE playlist_id=? AND group_id=?",iid,id(g))){v=copy(option);v.put("playlist_id",pid);v.put("group_id",gid);db.insertOrThrow("channel_group_options",null,v);}
      }
      groups.put(id(g),gid);
      if(stableKey==null&&!matches.isEmpty())stableKey=oldIdentities.get(gid);
      if(stableKey!=null){if(!emitted.add(stableKey))throw new IOException("Duplicate group identity.");nextMappings.put(new JSONObject().put("playlist",key).put("key",stableKey).put("id",gid).put("name",g.getAsString("name")));}

     }
     for(ContentValues old:oldGroups)if(managed.contains(id(old))&&!used.contains(id(old))){ContentValues retired=new ContentValues();retired.put("deleted_time",System.currentTimeMillis());update(db,"channel_groups",id(old),retired);
      String stable=oldIdentities.get(id(old));if(stable!=null&&emitted.add(stable))nextMappings.put(new JSONObject().put("playlist",key).put("key",stable).put("id",id(old)).put("name",old.getAsString("name")));
     }
     managed.addAll(groups.values());
     for(String name:incomingChannels.keySet()){
      ContentValues ch=incomingChannels.get(name),prior=oldChannels.get(name),v=fields(ch,CURATION);Long original=ch.getAsLong("original_group_id");set(v,"original_group_id",groups.get(original));set(v,"user_tvg_source_id",source(db,src,ch.getAsLong("user_tvg_source_id"),sources));v.putNull("deleted_time");long cid;
      if(prior!=null){cid=id(prior);update(db,"channels",cid,v);}else{ContentValues n=copy(ch);n.putAll(v);n.put("playlist_id",pid);n.put("last_group_playlist_id",pid);set(n,"last_group_id",groups.get(ch.getAsLong("last_group_id")));cid=db.insertOrThrow("channels",null,n);}
      for(long gid:managed){
       db.delete("channel_group_links","channel_id=? AND group_id=?",new String[]{""+cid,""+gid});
       db.delete("channel_manual_positions","channel_id=? AND type=4 AND playlist_id=? AND group_id=?",new String[]{""+cid,""+pid,""+gid});
      }
      for(ContentValues link:rows(src,"SELECT group_id FROM channel_group_links WHERE channel_id=?",id(ch))){Long gid=groups.get(link.getAsLong("group_id"));if(gid==null)throw new IOException("Missing group mapping.");v=new ContentValues();v.put("channel_id",cid);v.put("group_id",gid);db.insertOrThrow("channel_group_links",null,v);}
      for(ContentValues position:rows(src,"SELECT * FROM channel_manual_positions WHERE channel_id=?",id(ch))){Long gid=groups.get(position.getAsLong("group_id"));if(gid!=null){v=copy(position);v.put("channel_id",cid);v.put("playlist_id",pid);v.put("group_id",gid);db.insertOrThrow("channel_manual_positions",null,v);}}
      count++;
     }
     for(String name:oldChannels.keySet())if(name.matches("tidytivi-\\d+")&&!incomingChannels.containsKey(name)&&oldChannels.get(name).get("deleted_time")==null){ContentValues v=new ContentValues();v.put("deleted_time",System.currentTimeMillis());update(db,"channels",id(oldChannels.get(name)),v);}
     for(ContentValues assignment:rows(src,"SELECT * FROM playlist_tvg_source_assignments WHERE playlist_id=?",iid)){
      Long sid=source(db,src,assignment.getAsLong("tvg_source_id"),sources);
      if(rows(db,"SELECT id FROM playlist_tvg_source_assignments WHERE playlist_id=? AND tvg_source_id=?",pid,sid).isEmpty()){ContentValues v=new ContentValues();v.put("playlist_id",pid);set(v,"tvg_source_id",sid);set(v,"priority",assignment.get("priority"));db.insertOrThrow("playlist_tvg_source_assignments",null,v);}
     }
     ContentValues v=new ContentValues();v.put("name",ip.getAsString("name"));v.put("channel_count",incomingChannels.size());update(db,"playlists",pid,v);
    }
    VodMerge.apply(db);
    if(!rows(db,"PRAGMA foreign_key_check").isEmpty())throw new IOException("Merged backup failed its relationship check.");
    try(Cursor c=db.rawQuery("PRAGMA integrity_check",null)){if(!c.moveToFirst()||!"ok".equals(c.getString(0)))throw new IOException("Merged backup failed integrity validation.");}
    db.setTransactionSuccessful();
   }finally{db.endTransaction();}
   try(Cursor c=db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)){c.moveToFirst();}
   try(Cursor c=db.rawQuery("PRAGMA journal_mode=DELETE",null)){c.moveToFirst();}
   incomingManifest.put("group_mappings",nextMappings);return count;
  }finally{if(src!=null)src.close();db.close();}
 }
}
