package ayala.tidytivi.companion;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.io.IOException;

final class DriveLinks {
 static boolean host(String value){return "drive.google.com".equals(value)||"drive.usercontent.google.com".equals(value);}
 static String text(String s){return s.replace("&amp;","&").replace("&#39;","'").replace("&quot;","\"").replace("&lt;","<").replace("&gt;",">");}
 static Map<String,String> attrs(String tag){
  Map<String,String> values=new HashMap<>();Matcher m=Pattern.compile("([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*([\"'])(.*?)\\2",Pattern.DOTALL).matcher(tag);
  while(m.find())values.put(m.group(1).toLowerCase(Locale.US),text(m.group(3)));return values;
 }
 static Map<String,String> query(URI uri)throws Exception{
  Map<String,String> result=new LinkedHashMap<>();if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")){
   String[] pair=part.split("=",2);result.put(URLDecoder.decode(pair[0],"UTF-8"),pair.length>1?URLDecoder.decode(pair[1],"UTF-8"):"");
  }return result;
 }
 static String encode(Map<String,String> values)throws Exception{
  StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:values.entrySet()){
   if(b.length()>0)b.append('&');b.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));
  }return b.toString();
 }
 static String normalize(String value)throws Exception{
  URI uri=new URI(value);if(!"drive.google.com".equals(uri.getHost()))return value;
  Matcher m=Pattern.compile("^/file/d/([A-Za-z0-9_-]+)(?:/.*)?$").matcher(uri.getPath());
  if(!m.matches())return value;
  Map<String,String> q=query(uri);q.put("id",m.group(1));q.put("export","download");
  return "https://drive.google.com/uc?"+encode(q);
 }
 static String confirmation(String source,String html)throws Exception{
  URI origin=new URI(source);if(!host(origin.getHost())||!"https".equals(origin.getScheme()))throw new IOException();
  String expected=query(origin).get("id");
  Matcher forms=Pattern.compile("<form\\b([^>]*)>(.*?)</form>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
  while(forms.find()){
   Map<String,String> form=attrs(forms.group(1));if(!"get".equalsIgnoreCase((form.containsKey("method")?form.get("method"):"get")))continue;
   String action=form.get("action");if(action==null)continue;URI target=origin.resolve(action);
   if(!"https".equals(target.getScheme())||!host(target.getHost())||target.getUserInfo()!=null||target.getFragment()!=null||!"/download".equals(target.getPath()))continue;
   Map<String,String> q=query(target);Matcher inputs=Pattern.compile("<input\\b([^>]*)>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(forms.group(2));
   while(inputs.find()){
    Map<String,String> field=attrs(inputs.group(1));String name=field.get("name");
    if("hidden".equalsIgnoreCase(field.get("type"))&&Arrays.asList("id","export","confirm","uuid","resourcekey").contains(name))q.put(name,(field.containsKey("value")?field.get("value"):""));
   }
   if(!q.containsKey("resourcekey")&&query(origin).containsKey("resourcekey"))q.put("resourcekey",query(origin).get("resourcekey"));
   if(expected!=null&&expected.equals(q.get("id"))&&q.containsKey("confirm"))return "https://"+target.getHost()+"/download?"+encode(q);
  }throw new IOException("Drive did not provide a downloadable file.");
 }
}
