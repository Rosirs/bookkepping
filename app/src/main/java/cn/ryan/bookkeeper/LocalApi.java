package cn.ryan.bookkeeper;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Foreground-only, loopback-only, authenticated read API. No mutation routes. */
public final class LocalApi implements AutoCloseable {
    private final ServerSocket server;
    private final Thread worker;
    private final LedgerDb db;
    private final String token;
    private volatile Socket active;
    public LocalApi(LedgerDb db, String token) throws IOException {
        this.db=db; this.token=token;
        server=new ServerSocket(); server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),18765));
        worker=new Thread(this::run,"ledger-local-api"); worker.setDaemon(true); worker.start();
    }
    private void run() {
        while(!server.isClosed()) {
            try (Socket socket=server.accept()) {
                active=socket;
                if(server.isClosed()) return;
                socket.setSoTimeout(2000);
                try { handle(socket); } catch (Exception ignored) { /* malformed clients cannot stop listener */ }
            } catch(IOException ignored) { if(server.isClosed()) return; }
            finally { active=null; }
        }
    }
    private void handle(Socket socket) throws Exception {
        InputStream input=socket.getInputStream(); ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        int value; String header="";
        while(bytes.size()<8192 && (value=input.read())!=-1) {
            bytes.write(value);
            byte[] data=bytes.toByteArray(); int n=data.length;
            if(n>=4 && data[n-4]==13 && data[n-3]==10 && data[n-2]==13 && data[n-1]==10) { header=bytes.toString(StandardCharsets.US_ASCII.name()); break; }
        }
        if(header.isEmpty()) { respond(socket,400,"Bad Request","{}"); return; }
        String[] lines=header.split("\r\n"), request=lines[0].split(" "); String auth="";
        for(String line:lines) { int colon=line.indexOf(':'); if(colon>0 && line.substring(0,colon).equalsIgnoreCase("Authorization")) auth=line.substring(colon+1).trim(); }
        if(!MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),("Bearer "+token).getBytes(StandardCharsets.UTF_8))) {
            respond(socket,401,"Unauthorized","{\"error\":\"unauthorized\"}"); return;
        }
        if(request.length!=3 || !request[0].equals("GET")) { respond(socket,405,"Method Not Allowed","{}"); return; }
        try {
            URI uri=URI.create(request[1]); String month=java.time.YearMonth.now().toString();
            if(uri.getRawQuery()!=null) {
                if(!uri.getRawQuery().matches("month=[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException();
                month=LedgerRules.month(uri.getRawQuery().substring(6));
            }
            String body;
            switch(uri.getPath()) {
                case "/v1/summary": body=db.summary(month).toString(); break;
                case "/v1/entries": body=new JSONObject().put("month",month).put("entries",db.entries(month)).toString(); break;
                default: respond(socket,404,"Not Found","{}"); return;
            }
            respond(socket,200,"OK",body);
        } catch (IllegalArgumentException e) { respond(socket,400,"Bad Request","{\"error\":\"invalid_month\"}"); }
    }
    private void respond(Socket socket,int status,String reason,String body) throws IOException {
        byte[] data=body.getBytes(StandardCharsets.UTF_8);
        OutputStream out=socket.getOutputStream();
        out.write(("HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\nContent-Length: "+data.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(data); out.flush();
    }
    @Override public void close() {
        try { server.close(); } catch(IOException ignored) {}
        Socket socket=active;
        if(socket!=null) try { socket.close(); } catch(IOException ignored) {}
    }
}
