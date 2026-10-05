package ayala.tidytivi.companion;
import java.io.*;import java.nio.file.*;import java.util.zip.*;
public class TmbCodecTest {
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("tmb-codec-test").toFile();
  try{
   File original=new File(root,"original.zip");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(original))){z.putNextEntry(new ZipEntry("TvPlayer.db"));z.write("synthetic database".getBytes("UTF-8"));z.closeEntry();}
   File backup=new File(root,"backup.tmb"),decoded=new File(root,"decoded.zip");TmbCodec.encrypt(original,backup);TmbCodec.decrypt(backup,decoded);
   if(!java.util.Arrays.equals(Files.readAllBytes(original.toPath()),Files.readAllBytes(decoded.toPath())))throw new AssertionError("round trip");
   try(RandomAccessFile f=new RandomAccessFile(backup,"rw")){f.seek(40);f.write(f.read()^1);}
   try{TmbCodec.decrypt(backup,decoded);throw new AssertionError("accepted corruption");}catch(IOException expected){}
   if(decoded.exists())throw new AssertionError("partial plaintext left behind");
   if(args.length==2){TmbCodec.decrypt(new File(args[0]),new File(args[1]));System.out.println("Native backup authenticated and decoded.");}
   System.out.println("TmbCodec tests passed.");
  }finally{for(File f:root.listFiles())f.delete();root.delete();}
 }
}
