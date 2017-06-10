package com.nagorek.torrent.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class ServerClient implements Runnable{

	private PrintWriter out;
	private BufferedReader in;

	private Server server;
	private boolean running = false;

	private InetAddress address;
	private final int ID;
	private final int port;

	public ServerClient(Socket socket, Server server, int ID){

		this.ID = ID;
		this.port = 10000 + ID;
		this.server = server;
		this.address = socket.getInetAddress();

		try {			
			out = new PrintWriter(socket.getOutputStream());			
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		send("[port]" + port);
	}
	
	public InetAddress getAddress(){
		return address;
	}

	public int getID(){
		return ID;
	}

	public int getPort(){
		return port;
	}
	
	private void makeConnection(ServerClient sc, String fileData, int value){
		int port = sc.getPort();
		InetAddress address = sc.getAddress();	
		String data = "[data]" + String.valueOf(port) + address.toString() + "/" + fileData + "/" + value;
		send(data);
	}
	
	public void uploadFile(String host, String fileData){
		int value = server.checkData(host, null);
		if(value == 0){
			send("[invalidHost]");
			return;
		}
		String filename = fileData.split("@")[0];
		ServerClient sc = server.getClient(host);
		makeConnection(sc, fileData, 1);
		sc.send("[receiveFile]" + filename + "-" + fileData.split("@")[2] + "-" + fileData.split("@")[1] + 
				"-" + getAddress().toString() + "-" + ID);
		
		server.addRecord(address.toString() + ":" + filename + ":" + sc.getAddress().toString());
	}
	
	public void downloadFile(String host, String file){
		int value = server.checkData(host, file);
		if(value == 0){
			send("[invalidHost]");
			return;
		} else if (value == 1){
			send("[invalidFile]");
			return;
		}
		ServerClient sc = server.getClient(host);
		String fileData = server.getFileData(String.valueOf(sc.getID()), file, 4);

		makeConnection(sc, fileData, 0);
		sc.send("[sendFile]" + file + "-" + getAddress().toString());
		
		server.addRecord(sc.address.toString() + ":" + file + ":" + getAddress().toString());
	}
	
	public void resumeUpload(String host, String file){
		int value = server.checkData(host, null);
		if(value == 0){
			send("[inactiveHost]");
			return;
		}
		server.getClient(host).send("[uploadRequest]" + file + "/" + ID);
	}
	
	public void confirmUpload(String file, long currentSize, String host){
		ServerClient sc = server.getClient(host);
		String fileData = server.getFileData(String.valueOf(sc.getID()), file, 4);
		
		long sizeLeft = Long.parseLong(fileData.split("@")[2]) - currentSize;
		fileData = fileData.split("@")[0] + "@" + fileData.split("@")[1] + "@" + currentSize;
		
		makeConnection(sc, fileData, 3);
		sc.send("[rereceiveFile]" + file + "-" + sizeLeft + "-" + fileData.split("@")[1] + "-" + getAddress().toString() + "-" + ID);	
	}
	
	public void resumeDownload(String host, String file, long currentSize){
		int value = server.checkData(host, file);
		if(value == 0){
			send("[inactiveHost]");
			return;
		} else if (value == 1){
			send("[removedFile]");
			return;
		}

		ServerClient sc = server.getClient(host);
		String fileData = server.getFileData(String.valueOf(sc.getID()), file, 4);
		long sizeLeft = Long.parseLong(fileData.split("@")[2]) - currentSize;
		fileData = fileData.split("@")[0] + "@" + fileData.split("@")[1] + "@" + sizeLeft;
		
		makeConnection(sc, fileData, 2);
		sc.send("[resendFile]" + file + "-" + currentSize + "-" + getAddress().toString());	
	}
	
	public void sendList(String list){
		send("[list]" + list);
	}
	
	private void send(String text){
		out.println(text);
		out.flush();
	}
	
	private void process(String text){
		String prefix = text.substring(0, text.indexOf("]") + 1);
		String value = text.substring(prefix.length(), text.length());	
		if(prefix.equals("[list]")){
			if(value.equals("get")){
				sendList(server.getList());
			} else {
				List<String >files = Arrays.asList(value.split(":"));
				server.updateList(ID, files);
			}
		} else if(prefix.equals("[download]")){
			downloadFile(value.split("/")[0], value.split("/")[1]);
		} else if(prefix.equals("[upload]")){
			uploadFile(value.split("/")[0], value.split("/")[1]);
		} else if(prefix.equals("[confirmReceive]")){
			server.removeRecord(value + address.toString());
		} else if(prefix.equals("[confirmDownload]")){
			server.removeRecord(address.toString() + value);
		} else if(prefix.equals("[history]")){
			send("[history]" + server.getRecords(address.toString()));
		} else if(prefix.equals("[resumeDownload]")){
			resumeDownload(value.split("/")[0], value.split("/")[1], Long.parseLong(value.split("/")[2]));
		} else if(prefix.equals("[resumeUpload]")){
			resumeUpload(value.split("/")[0], value.split("/")[1]);
		} else if(prefix.equals("[confirmUpload]")){
			server.getClient(value.split("/")[2]).confirmUpload(value.split("/")[0], Long.parseLong(value.split("/")[1]), String.valueOf(ID));
		} else if(prefix.equals("[check]")){
			String response = value.split("/")[0];
			String file = value.split("/")[1];
			ServerClient sc = server.getClient(value.split("/")[2]);
			if(response.equals("correct")){
				sc.send("[check]correct/" + file);
			} else if(response.equals("incorrect")){
				sc.send("[check]incorrect/" + file);
			}
		}
	}	

	public void run() {		
		try {
			running = true;
			while(running){
				String text;
				while((text = in.readLine()) != null){
					process(text);
				}
			}
		} catch (IOException e) {
			try {
				running = false;
				in.close();
				out.close();
				server.disconnect(this);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
}
