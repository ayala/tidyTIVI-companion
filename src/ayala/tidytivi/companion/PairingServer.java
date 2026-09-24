package ayala.tidytivi.companion;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** A short-lived, token-protected setup page. Never serves stored links or files. */
final class PairingServer implements Closeable {
 interface Receiver { void save(String link) throws Exception; void complete(); }
 private final ServerSocket server;
 private final String token, page, origin;
 private final Receiver receiver;
 private final long deadline = System.nanoTime() + 10L * 60 * 1000000000;
 private volatile boolean closed;
 private volatile Socket active;
 private boolean used;
 PairingServer(InetAddress address, String page, Receiver receiver) throws IOException {
  this.receiver=receiver; this.page=page;
  byte[] random=new byte[24];new SecureRandom().nextBytes(random);
  StringBuilder text=new StringBuilder();for(byte b:random)text.append(String.format(Locale.US,"%02x",b&255));token=text.toString();
  server=new ServerSocket();server.bind(new InetSocketAddress(0));server.setSoTimeout(1000);
  origin="http://"+address.getHostAddress()+":"+server.getLocalPort();
 }
 String url(){return origin+"/"+token;}
 void start(){Thread t=new Thread(()->{while(!closed && System.nanoTime()<deadline){
   try(Socket client=server.accept()){active=client;client.setSoTimeout(3000);handle(client);}
   catch(IOException ignored){}finally{active=null;}
  }close();},"tidytivi-pairing");t.setDaemon(true);t.start();}
 private static String line(InputStream in,int max)throws IOException{
  ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c=='\n')return b.toString("US-ASCII").replace("\r","");if(b.size()>=max)throw new IOException();b.write(c);}throw new EOFException();
 }
 private void handle(Socket socket)throws IOException{
  InputStream in=socket.getInputStream();String[] request=line(in,2048).split(" ");
  if(request.length!=3){reply(socket,400,"Invalid request.");return;}
  Map<String,String> headers=new HashMap<>();int size=0;
  while(true){String h=line(in,8192);size+=h.length();if(size>12288)throw new IOException();if(h.isEmpty())break;int colon=h.indexOf(':');if(colon<1)throw new IOException();String key=h.substring(0,colon).toLowerCase(Locale.US);if(headers.put(key,h.substring(colon+1).trim())!=null)throw new IOException();}
  if(!request[1].equals("/"+token)||used||System.nanoTime()>=deadline){reply(socket,404,"Setup link expired. Open Connect on your TV.");return;}
  if(!origin.substring(7).equals(headers.get("host")) || (headers.containsKey("origin")&&!origin.equals(headers.get("origin")))){reply(socket,403,"Open the QR link on your TV.");return;}
  if("GET".equals(request[0])){reply(socket,200,page);return;}
  if(!"POST".equals(request[0])){reply(socket,405,"Use the setup page.");return;}
  if(headers.containsKey("transfer-encoding")||!headers.getOrDefault("content-type","").startsWith("application/x-www-form-urlencoded")){reply(socket,400,"Invalid form.");return;}
  int length;try{length=Integer.parseInt(headers.getOrDefault("content-length","0"));}catch(Exception e){length=0;}
  if(length<1||length>8192){reply(socket,400,"Invalid link length.");return;}
  byte[] bytes=new byte[length];new DataInputStream(in).readFully(bytes);
  String link=null;try{for(String part:new String(bytes,StandardCharsets.UTF_8).split("&")){String[] pair=part.split("=",2);if(pair.length==2&&pair[0].equals("url")){if(link!=null)throw new IOException();link=URLDecoder.decode(pair[1],"UTF-8");}}
   if(link==null||link.trim().length()>4096)throw new IOException();receiver.save(link.trim());used=true;
  }catch(Exception e){reply(socket,400,"<meta name='viewport' content='width=device-width,initial-scale=1'><p>Enter a valid HTTPS download link.</p><button onclick='history.back()'>Go back</button>");return;}
  try{reply(socket,200,"<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Connected</title><body style='background:#0f1620;color:white;font:20px system-ui;text-align:center;padding:60px 20px'>Connected.<p style='font-size:16px;color:#ccc'>You can close this page.</p></body>");}
  finally{receiver.complete();close();}
 }
 private void reply(Socket s,int code,String body)throws IOException{
  byte[] bytes=body.getBytes(StandardCharsets.UTF_8);String headers="HTTP/1.1 "+code+" "+(code==200?"OK":"Error")+"\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n";
  OutputStream out=s.getOutputStream();out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
 }
 public void close(){closed=true;try{server.close();}catch(IOException ignored){}Socket s=active;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
