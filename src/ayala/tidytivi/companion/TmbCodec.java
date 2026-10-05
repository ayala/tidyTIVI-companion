package ayala.tidytivi.companion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Authenticated TiviMate v1 envelopes; no user account credentials are embedded. */
final class TmbCodec {
 private static final long LIMIT=1024L*1024*1024;
 private static byte[] unhex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return b;}
 // Application-format constants; these are not a user's password or provisioned key.
 private static final String PASSWORD_HEX="020030006200300060000d001600190037002a002f00020062000600040001001c000d00020001004000020004006c00040037000600";
 private static final String SUFFIX_HEX="6b39486d3478527051326e5662543777";
 private static byte[] key(byte[] header)throws Exception{
  if(header.length!=21||header[0]!=1||header[17]!=0||header[18]!=1||(header[19]&255)!=0x86||(header[20]&255)!=0xa0)throw new IOException("Unsupported TiviMate backup format.");
  byte[] pass=new String(unhex(PASSWORD_HEX),StandardCharsets.UTF_16LE).getBytes(StandardCharsets.UTF_8);
  byte[] salt=new byte[36];System.arraycopy(header,1,salt,0,16);System.arraycopy(unhex(SUFFIX_HEX),0,salt,16,16);salt[35]=1;
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(pass,"HmacSHA256"));byte[] u=mac.doFinal(salt),result=u.clone();
  for(int i=1;i<100000;i++){u=mac.doFinal(u);for(int j=0;j<32;j++)result[j]^=u[j];}
  Arrays.fill(pass,(byte)0);return result;
 }
 static void decrypt(File backup,File zip)throws Exception{
  long length=backup.length();if(length<69||length>LIMIT)throw new IOException("Invalid backup size.");
  byte[] header=new byte[21],iv=new byte[16],tag=new byte[32];
  try(RandomAccessFile in=new RandomAccessFile(backup,"r")){
   in.readFully(header);in.readFully(iv);in.seek(length-32);in.readFully(tag);byte[] key=key(header);
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));in.seek(0);byte[] b=new byte[65536];long remaining=length-32;
   while(remaining>0){int n=in.read(b,0,(int)Math.min(b.length,remaining));if(n<0)throw new EOFException();mac.update(b,0,n);remaining-=n;}
   if(!MessageDigest.isEqual(tag,mac.doFinal()))throw new IOException("Backup authentication failed. Select a complete TiviMate backup.");
   Cipher cipher=Cipher.getInstance("AES/CTR/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));in.seek(37);remaining=length-69;
   try(OutputStream out=new FileOutputStream(zip)){while(remaining>0){int n=in.read(b,0,(int)Math.min(b.length,remaining));if(n<0)throw new EOFException();out.write(cipher.update(b,0,n));remaining-=n;}out.write(cipher.doFinal());}
   Arrays.fill(key,(byte)0);
  }catch(Exception e){zip.delete();throw e;}
 }
 static void encrypt(File zip,File backup)throws Exception{
  byte[] header=new byte[21],iv=new byte[16];new SecureRandom().nextBytes(header);new SecureRandom().nextBytes(iv);header[0]=1;header[17]=0;header[18]=1;header[19]=(byte)0x86;header[20]=(byte)0xa0;
  byte[] key=key(header);Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));Cipher cipher=Cipher.getInstance("AES/CTR/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));
  try(InputStream in=new FileInputStream(zip);OutputStream out=new FileOutputStream(backup)){
   out.write(header);out.write(iv);mac.update(header);mac.update(iv);byte[] b=new byte[65536];int n;long total=0;
   while((n=in.read(b))!=-1){if((total+=n)>LIMIT)throw new IOException("Backup too large.");byte[] encrypted=cipher.update(b,0,n);out.write(encrypted);mac.update(encrypted);}
   byte[] last=cipher.doFinal();out.write(last);mac.update(last);out.write(mac.doFinal());
  }catch(Exception e){backup.delete();throw e;}finally{Arrays.fill(key,(byte)0);}
 }
 static void unpack(File zip,File directory)throws Exception{
  if(!directory.mkdir())throw new IOException("Cannot create backup workspace.");String root=directory.getCanonicalPath()+File.separator;Set<String> seen=new HashSet<>();long total=0;
  try(ZipInputStream in=new ZipInputStream(new FileInputStream(zip))){ZipEntry e;byte[] b=new byte[65536];
   while((e=in.getNextEntry())!=null){String name=e.getName();File dest=new File(directory,name);
    if(name.startsWith("/")||name.contains("\\")||!dest.getCanonicalPath().startsWith(root)||!seen.add(dest.getCanonicalPath())||seen.size()>100)throw new IOException("Invalid backup paths.");
    if(e.isDirectory()){if(!dest.mkdirs()&&!dest.isDirectory())throw new IOException();continue;}
    if(!dest.getParentFile().mkdirs()&&!dest.getParentFile().isDirectory())throw new IOException();
    try(OutputStream out=new FileOutputStream(dest)){int n;while((n=in.read(b))!=-1){if((total+=n)>LIMIT)throw new IOException("Backup exceeds extraction limit.");out.write(b,0,n);}}
   }
  }
  if(!new File(directory,"TvPlayer.db").isFile())throw new IOException("Backup has no TiviMate database.");
 }
 static void pack(File directory,File zip)throws Exception{try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){pack(directory,directory,out);}}
 private static void pack(File root,File dir,ZipOutputStream out)throws Exception{
  File[] entries=dir.listFiles();if(entries==null)throw new IOException();Arrays.sort(entries,Comparator.comparing(File::getName));byte[] b=new byte[65536];
  for(File f:entries){if(f.isDirectory()){pack(root,f,out);continue;}String name=root.toURI().relativize(f.toURI()).getPath();if(name.endsWith("-wal")||name.endsWith("-shm"))throw new IOException("Uncheckpointed backup database.");out.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))!=-1)out.write(b,0,n);}out.closeEntry();}
 }
}
