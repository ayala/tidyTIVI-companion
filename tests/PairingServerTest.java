package ayala.tidytivi.companion;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.AtomicInteger;
public class PairingServerTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static String request(PairingServer server,String method,String path,String body,String origin)throws Exception{
  URI u=new URI(server.url());try(Socket s=new Socket("127.0.0.1",u.getPort())){s.setSoTimeout(4000);String text=method+" "+path+" HTTP/1.1\r\nHost: "+u.getAuthority()+"\r\n"+(origin==null?"":"Origin: "+origin+"\r\n")+"Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "+body.getBytes(StandardCharsets.UTF_8).length+"\r\n\r\n"+body;s.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;while((n=s.getInputStream().read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8");}
 }
 public static void main(String[] args)throws Exception{
  AtomicInteger saves=new AtomicInteger(),done=new AtomicInteger();PairingServer.Receiver receiver=new PairingServer.Receiver(){public void save(String link)throws Exception{if(!link.equals("https://example.org/bundle.zip?a=1&b=2"))throw new IOException();saves.incrementAndGet();}public void complete(){done.incrementAndGet();}};
  PairingServer p=new PairingServer(InetAddress.getLoopbackAddress(),"<form>setup</form>",receiver);p.start();String path=new URI(p.url()).getPath();
  try(Socket idle1=new Socket("127.0.0.1",new URI(p.url()).getPort());Socket idle2=new Socket("127.0.0.1",new URI(p.url()).getPort())){
   long started=System.nanoTime();check(request(p,"GET",path,"",null).contains("<form>setup</form>"));check(System.nanoTime()-started<2000000000L);
  }
  check(request(p,"GET",path,"",null).contains("Referrer-Policy: same-origin"));
  check(request(p,"GET","/wrong","",null).startsWith("HTTP/1.1 404"));
  check(request(p,"POST",path,"url=bad",null).startsWith("HTTP/1.1 400"));
  check(request(p,"POST",path,"url=bad","https://other.example").startsWith("HTTP/1.1 403"));
  check(request(p,"POST",path,"url="+URLEncoder.encode("https://example.org/bundle.zip?a=1&b=2","UTF-8"),null).contains("Connected."));
  check(saves.get()==1);for(int i=0;i<50&&done.get()==0;i++)Thread.sleep(10);check(done.get()==1);
  check(request(p,"GET",path,"",null).contains("Connected."));
  check(request(p,"POST",path,"url=another",null).contains("Connected."));check(saves.get()==1);p.close();
  try{request(p,"GET",path,"",null);throw new AssertionError();}catch(IOException expected){}
  PairingServer q=new PairingServer(InetAddress.getLoopbackAddress(),"setup",receiver);q.start();check(!new URI(q.url()).getPath().equals(path));q.close();try{request(q,"GET",new URI(q.url()).getPath(),"",null);throw new AssertionError();}catch(IOException expected){}
  System.out.println("Pairing server: page, token, validation, origin, save, one-use, cancellation passed.");
 }
}
