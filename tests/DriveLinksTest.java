package ayala.tidytivi.companion;
public class DriveLinksTest {
 public static void main(String[] args)throws Exception{
  String normalized=DriveLinks.normalize("https://drive.google.com/file/d/test-id/view?resourcekey=key");
  if(!normalized.contains("id=test-id")||!normalized.contains("resourcekey=key"))throw new AssertionError();
  String form="<form method='get' action='https://drive.usercontent.google.com/download'><input type='hidden' name='id' value='test-id'><input name='confirm' value='t' type='hidden'><input type='hidden' name='uuid' value='abc&amp;def'></form>";
  String result=DriveLinks.confirmation(normalized,form);
  if(!result.contains("confirm=t")||!result.contains("uuid=abc%26def"))throw new AssertionError();
  for(String bad:new String[]{form.replace("drive.usercontent.google.com","evil.test"),form.replace("test-id","wrong-id"),form.replace("https://drive.usercontent","http://drive.usercontent")}){
   boolean rejected=false;try{DriveLinks.confirmation(normalized,bad);}catch(Exception e){rejected=true;}if(!rejected)throw new AssertionError();
  }
  System.out.println("Drive link normalization and confirmation validation passed");
 }
}
