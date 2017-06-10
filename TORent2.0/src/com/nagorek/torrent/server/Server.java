package com.nagorek.torrent.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Server implements Runnable{

	private ServerSocket serverSocket;
	private Socket clientSocket;
	private Server server;
	
	private Thread thread, listen;
	private volatile boolean running = false;
		
	private volatile List<String> usersDataBase = new ArrayList<>();
	private volatile List<ServerClient> activeUsers = new ArrayList<>();
	private int count = 1;
	
	private volatile Map<Integer, List<String>> files = new HashMap<>();
	private volatile List<String> history = new ArrayList<>(); // format = sender:file:receiver
	
	public Server(int port){
		try {
			serverSocket = new ServerSocket(port);
			server = this;
		} catch (IOException e) {
			System.out.println("Couldn't start the server");
			System.exit(1);
		}
		thread = new Thread(this);
		thread.start();
		System.out.println("Server listening on port " + port);
	}	
	
	public void addRecord(String record){
		if(!history.contains(record)) history.add(record);
	}
	
	public void removeRecord(String record){
		history.remove(record);
	}
	
	public String getRecords(String address){
		StringBuilder sb = new StringBuilder();
		String sender = "";
		String receiver = "";
		String file = "";
		for(String s : history){		
			sender = s.split(":")[0];
			file = s.split(":")[1];
			receiver = s.split(":")[2];
			
			if(sender.equals(address)){
				sb.append("s:" + file + ":" + getID(receiver) + "/");
			}
			if(receiver.equals(address)){
				sb.append("r:" + file + ":" + getID(sender) + "/");
			}
			
		}
		return sb.toString(); 				// s:file:to|s:file:to|r:file:from| etc
	}
	
	private int getID(String address){
		for(String s : usersDataBase){
			if(s.split(":")[0].equals(address)){
				return Integer.parseInt(s.split(":")[1]);
			}
		}
		return 0;
	}
	
	public String getFileData(String host, String file, int part){ // return file info 0 - name, 1 - checksum, 2 - size
		List<String> fileList = files.get(Integer.parseInt(host));
		for(int i = 0; i < fileList.size(); i++){
			String value = fileList.get(i).split("@")[0];
			if(value.equals(file)){
				if(part == 0){
					return value;
				} else if(part == 1){
					return fileList.get(i).split("@")[1];
				} else if(part == 2){
					return fileList.get(i).split("@")[2];
				} else if(part == 4){
					return fileList.get(i);
				}
			}
		}
		return "";
	}
	
	public ServerClient getClient(String host){	//find host with given id
		int i = 0;
		int ID = Integer.parseInt(host);
		ServerClient sc = null;
		do{ 
			if(activeUsers.get(i).getID() == ID) {
				sc = activeUsers.get(i);
			}
			i++;
		} while(sc == null);
		return sc;
	}
	
	public int checkData(String host, String file){ //find specific file at specific host 1-host exist, 2-file exist
		ServerClient sc = null;
		int ID = Integer.parseInt(host);
		int count = 0;
		for(int i = 0; i < activeUsers.size(); i++){
			sc = activeUsers.get(i);
			if(sc.getID() == ID) {
				count++;
				if(file == null){
					return count;
				}
				if(!getFileData(host, file, 0).equals("")){
					count++;
				}		
			}
		}
		return count;
	}
	
	public String getList(){	//return hosts + their files :: host:files/host:files/ etc
		StringBuffer sb = new StringBuffer();
		ServerClient sc = null;
		for(int i = 0; i < activeUsers.size(); i++){
			sc = activeUsers.get(i);
			sb.append(sc.getID() + ":");
			List<String> userFiles = files.get(sc.getID());		
			for(String s : userFiles){
				sb.append(s + "/");
			}
			sb.append("!");
		}	
		return sb.toString();
	}		
	
	public void updateList(int ID, List<String> fileList){	//add host and its files
		if(files.containsKey(ID)){
			files.replace(ID, fileList);
		} else {
			files.put(ID, fileList);
		}
	}
	
	public void disconnect(ServerClient sc){	//when disconnected host and his files are removed
		activeUsers.remove(sc);
		files.remove(sc.getID());
		System.out.println("User " + sc.getID() + " disconnected");
	}
	
	private void listen(){
		listen = new Thread(new Runnable(){
			public void run() {
				while(running){
					try {
						clientSocket = serverSocket.accept();
						System.out.println("New connection: " + clientSocket.getInetAddress() + ", ID = " + count);
						ServerClient sc = new ServerClient(clientSocket, server, count++);
						activeUsers.add(sc);
						usersDataBase.add(sc.getAddress().toString() + ":" + sc.getID());
						new Thread(sc).start();
					} catch (IOException e) {
						System.out.println("Server socket closed.");
					}
				}
			}			
		});
		listen.start();
	}
	
	public void run() {
		running = true;
		listen();
		Scanner scanner = new Scanner(System.in);
		String text = "";
		while(running){
			text = scanner.nextLine();
			if(text.equals("exit")){
				running = false;
				System.exit(0);
			} else if(text.equals("history")){
				for(String s : history){
					System.out.println(s);
				}
			}
		}
		scanner.close();
		//closing 
		System.out.println("Server shutdown");		
	}
	
	public static void main(String[] args) {
		new Server(32310);
	}
}
