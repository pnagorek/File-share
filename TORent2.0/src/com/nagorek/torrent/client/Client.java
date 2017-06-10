package com.nagorek.torrent.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class Client implements Runnable{
	
	private Socket socket;
	private ClientConsole console;
	
	private PrintWriter out;
	private BufferedReader in;
	
	private Thread thread, listen;
	
	private List<String> files = new ArrayList<>();
	
	private ServerSocket serverSocket;
	private volatile List<Socket> sockets = new ArrayList<>();
	
	private File destination;
	private File source;

	public Client(String host, int port, ClientConsole console){
		try {		
			socket = new Socket(host, port);
			out = new PrintWriter(socket.getOutputStream());
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.console = console;
		} catch (IOException e) {
			System.out.println("Couldn't connect to the server");;
			System.exit(1);
		}	
		thread = new Thread(this);
		thread.start();
		System.out.println("Server up and running");
	}
	
	public void getUnfinishedTransfers(){
		send("[history]!");
	}
	
	public String calculateMD5(String file) throws Exception{
		MessageDigest md = MessageDigest.getInstance("MD5");
	    FileInputStream fis = new FileInputStream(file);

	    byte[] buffer = new byte[1024];

	    int length = 0;
	    while ((length = fis.read(buffer)) != -1) {
	        md.update(buffer, 0, length);
	    };
	    byte[] mdbytes = md.digest();
	    StringBuffer sb = new StringBuffer();
	    for (int i = 0; i < mdbytes.length; i++) {
	        sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
	    }
	    fis.close();
	    return sb.toString();
	}
	
	private Socket waitForConnection(String address){
		Socket socket = null;
		boolean connected = false;
		while(!connected){
			for(int i = 0; i < sockets.size(); i++){
				if(sockets.get(i).getInetAddress().toString().equals(address)){
					socket = sockets.get(i);
					sockets.remove(socket);
					connected = true;
				}
			}
		}
		return socket;
	}
	
	public void startUpload(String address, int port, String fileName, long skip, String checksum){
		new Thread(){
			public void run(){		
				File file = new File(source.getAbsolutePath() + "/" + fileName);
				try (Socket socket = new Socket(address, port);
						BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
						BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream())){			
					
					bis.skip(skip);				
					int size = (int)(file.length() - skip);

					byte[] buffer = new byte[Math.min((int)size, 1024 * 1024)];
					int length;
					System.out.println();
					System.out.println("Uploading file: " + file.getName() + ", size: " + size);

					long startTime = System.currentTimeMillis();
					long uploaded = 0;
					
					while((length = bis.read(buffer)) > 0){
						bos.write(buffer, 0, length);
						uploaded += length;
						long now = System.currentTimeMillis();
						if(now - startTime > 1000){
							System.out.println((uploaded * 100) / size + "%");
							startTime = now;
						}
					}						
				} catch (Exception e) {
					System.out.println("Connection lost.");
				} 
			}
		}.start();
	}
	
	public void startDownload(String address, int port, String fileName, long size, String checksum, boolean append){		
		new Thread(){
			public void run(){
				File file = new File(destination.getAbsolutePath() + "/" + fileName);
				try (Socket socket = new Socket(address, port);							
						BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
						BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file, append))){	
					
					int startSize = (int)file.length();

					byte[] buffer = new byte[Math.min((int)size, 1024 * 1024)];
					int length;
					
					if(append){
						System.out.println();
						System.out.println("Resuming file's download: " + file.getName() + ", size left: " + size);
					} else{
						System.out.println();
						System.out.println("Downloading new file: " + file.getName() + ", size: " + size);
					}
					
					long startTime = System.currentTimeMillis();

					while((length = bis.read(buffer)) > 0){
						bos.write(buffer, 0, length);	
						long now = System.currentTimeMillis();
						if(now - startTime > 1000){
							System.out.println(((file.length() - startSize)* 100) / size + "%");
							startTime = now;
						}
					}
					bos.flush();
					System.out.println("Checking integrity...");
					String md5 = calculateMD5(file.getAbsolutePath());
					if(!md5.equals(checksum)) System.out.println("File corrupted");
					else System.out.println("File successfully downloaded");

					files.add(file.getName() + "@" + md5 + "@" + file.length());
					sendList(files);
					send("[confirmDownload]" + ":" + fileName + ":/" + address);
				} catch (Exception e) {
					System.out.println("Connection lost.");
				}
			}
		}.start();		
	}
	
	private void receiveFile(String fileName, long size, String checksum, String address, String ID, boolean append){
		new Thread(){
			public void run(){
				File file = new File(destination.getAbsolutePath() + "/" + fileName);
				try(Socket socket = waitForConnection(address);
						BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
						BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file, append));) {

					byte[] buffer = new byte[(int) Math.min(size, 1024)];
					int length;
					System.out.println();
					System.out.println("Receiving new file: " + file.getName());

					while((length = bis.read(buffer)) > 0){
						bos.write(buffer, 0, length);			
					}
					bos.flush();
					System.out.println("Checking integrity...");
					String md5 = calculateMD5(file.getAbsolutePath());
					if(!md5.equals(checksum)) send("[check]incorrect/" + fileName + "/" + ID);
					else send("[check]correct/" + fileName + "/" + ID);

					files.add(file.getName() + "@" + md5 + "@" + file.length());
					sendList(files);
					System.out.println("File received");
					System.out.println();
					send("[confirmReceive]" + address + ":" + fileName + ":");
				} catch (Exception e) {
					System.out.println("Connection lost.");
				}
			}
		}.start();
	}
	
	private void sendFile(String fileName, long skip, String address){
		new Thread(){
			public void run(){	
				File file = new File(source.getAbsolutePath() + "/" + fileName);
				try(Socket socket = waitForConnection(address);					
						BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
						BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream())) {
					
					bis.skip(skip);
					
					int size = (int)(file.length() - skip);

					byte[] buffer = new byte[Math.min(size, 1024 * 1024)];
					int length;
					System.out.println();
					System.out.println("Sending file: " + file.getName() + ", size left: " + size);

					while((length = bis.read(buffer)) > 0){
						bos.write(buffer, 0, length);	
					}
					bos.flush();
					System.out.println("File sent");
					System.out.println();			
				} catch (Exception e) {
					System.out.println("Connection lost.");
				}
			}
		}.start();
	}	
	
	public void resumeUpload(String host, String filename){
		send("[resumeUpload]" + host + "/" + filename);
	}
	
	public void resumeDownload(String host, String filename){
		File[] fileList = destination.listFiles();
		long size = 0;
		for(File f : fileList){
			if(f.getName().equals(filename)){
				size = f.length();
			}
		}
		send("[resumeDownload]" + host + "/" + filename + "/" + size);
	}
	
	public void upload(String host, String fileName){
		String fileData = "";
		boolean exist = false;
		for(String f : files){
			if(f.split("@")[0].equals(fileName)){
				exist = true;
				fileData = f;
			}
		}
		if(!exist){
			System.out.println("File not found");
		} else {
			send("[upload]" + host + "/" + fileData);
		}
	}
	
	public void download(String host, String file){
		send("[download]" + host + "/" + file);
	}
	
	public void getFileList(){
		send("[list]get");
	}
	
	private void sendList(List<String> files){
		StringBuffer sb = new StringBuffer();
		if(!files.isEmpty()){
			for(int i = 0; i < files.size(); i++){
				sb.append(files.get(i) + ":");
			}
		}
		send("[list]" + sb.toString());
	}
	
	public void send(String text){
		out.println(text);
		out.flush();
	}
	
	private void getFiles(String res){
		File[] fileList = new File(res).listFiles();
		if(fileList != null) {
			String md5 = "";
			files.clear();
			System.out.println("Generating checksum...");
			for(int i = 0; i < fileList.length; i++){
				File file = fileList[i];
				try {
					md5 = calculateMD5(file.getAbsolutePath());
					files.add(file.getName() + "@" + md5 + "@" + file.length());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		sendList(files);
		console.start();
	}
	
	private long getFileSize(String filename){
		File[] fileList = destination.listFiles();
		long size = 0;
		for(File f : fileList){
			if(f.getName().equals(filename)){
				size = f.length();
			}
		}
		return size;
	}
		
	private void listen(){
		listen = new Thread(new Runnable(){
			public void run() {
				while(true){
					try {
						sockets.add(serverSocket.accept());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}			
		});
		listen.start();
	}
	
	public void run() {
		try {
			String text;
			while((text = in.readLine()) != null){
				String prefix = text.substring(0, text.indexOf("]") + 1);
				String value = text.substring(prefix.length(), text.length());	
				if(prefix.equals("[port]")){
					int port = Integer.parseInt(value);
					int ID = port - 10000;
					System.out.println("Your ID: " + ID);
					serverSocket = new ServerSocket(port);
					listen();
					String os = System.getProperty("os.name");
					String name = "";
					if(os.startsWith("Windows")){
						name = "D:/TORrent_" + ID;
					} else if(os.startsWith("Linux")){
						name = "~/TORrent_" + ID;
					}
					new File(name).mkdir();
					destination = new File(name);
					source = new File(name);
					getFiles(source.getAbsolutePath());
				} else if(prefix.equals("[list]")){
					console.printList(value);
				} else if(prefix.equals("[invalidHost]")){
					System.out.println("Host doesn't exist");
				} else if(prefix.equals("[invalidFile]")){
					System.out.println("File doesn't exist");
				} else if(prefix.equals("[inactiveHost]")){
					System.out.println("Host offline");
				} else if(prefix.equals("[removedFile]")){
					System.out.println("File no longer exist");
				}else if(prefix.equals("[data]")){
					int port = Integer.parseInt(value.split("/")[0]);
					String address = value.split("/")[1];
					String fileData = value.split("/")[2];
					String fileName = fileData.split("@")[0];
					String checksum = fileData.split("@")[1];
					long size = Integer.parseInt(fileData.split("@")[2]);
					int option = Integer.valueOf(value.split("/")[3]);
					if(option == 0){
						startDownload(address, port, fileName, size, checksum, false);
					} else if(option == 1){
						startUpload(address, port, fileName, 0, checksum);
					} else if(option == 2){
						startDownload(address, port, fileName, size, checksum, true);
					} else if(option == 3){
						startUpload(address, port, fileName, size, checksum);
					}
				} else if(prefix.equals("[sendFile]")){
					sendFile(value.split("-")[0], 0, value.split("-")[1]);
				} else if(prefix.equals("[receiveFile]")){
					receiveFile(value.split("-")[0], Long.parseLong(value.split("-")[1]), value.split("-")[2], value.split("-")[3], value.split("-")[4], false);
				} else if(prefix.equals("[resendFile]")){
					sendFile(value.split("-")[0], Long.parseLong(value.split("-")[1]), value.split("-")[2]);
				} else if(prefix.equals("[history]")){
					console.printHistory(value);
				} else if(prefix.equals("[uploadRequest]")){
					send("[confirmUpload]" + value.split("/")[0] + "/" + getFileSize(value.split("/")[0]) + "/" + value.split("/")[1]);
				} else if(prefix.equals("[rereceiveFile]")){
					receiveFile(value.split("-")[0], Long.parseLong(value.split("-")[1]), value.split("-")[2], value.split("-")[3], value.split("-")[4], true);
				} else if(prefix.equals("[check]")){
					String response = value.split("/")[0];
					String file = value.split("/")[1];
					if(response.equals("correct")){
						System.out.println(file + " successfully uploaded");
					} else if(response.equals("incorrect")){
						System.out.println(file + " upload was corrupted");
					}
				}
			}
		} catch (IOException e) {
			System.out.println("Server offline");
		}
	}			
}
