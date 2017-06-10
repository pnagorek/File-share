package com.nagorek.torrent.client;

import java.util.Scanner;

public class ClientConsole {
	
	private Client client;	
	private String download, upload;
	private volatile boolean running = false;
	
	public ClientConsole(String host, int port){
		client = new Client(host, port, this);
	}
	
	public void getList(){
		client.getFileList();
	}
	
	public void printList(String text){
		String[] tab = text.split("!");
		for(int i = 0; i < tab.length; i++){
			String user = tab[i].substring(0, tab[i].indexOf(":"));
			System.out.println();
			System.out.println("User " + user + "'s files:");
			System.out.println("------------------------------------------------------");
			String file = tab[i].substring(tab[i].indexOf(":") + 1, tab[i].length());
			String[] files = file.split("/");
			for(int j = 0; j < files.length; j++){
				System.out.println("file: " + files[j].split("@")[0]);
				System.out.println("size: " + files[j].split("@")[2] + " bytes");
				System.out.println("checksum: " + files[j].split("@")[1]);
				System.out.println();
			}			
			System.out.println("------------------------------------------------------");
		}
	}
	
	private void downloadFile(String host, String file){
		client.download(host, file);
	}
	
	private void uploadFile(String host, String file){
		client.upload(host, file);
	}
	
	private void getUnfinishedTransfers(){
		client.getUnfinishedTransfers();
	}
	
	public void printHistory(String history){
		String[] tab = history.split("/");
		StringBuilder download = new StringBuilder();
		StringBuilder upload = new StringBuilder();
		int countD = 0;
		int countU = 0;
		
		for(String s : tab){
			if(s.split(":")[0].equals("s")) download.append(++countD + ") " + s.split(":")[1] + " to " + s.split(":")[2] + "\n");
			if(s.split(":")[0].equals("r")) upload.append(++countU + ") " + s.split(":")[1] + " from " + s.split(":")[2] + "\n");
		}
		
		this.download = download.toString();
		this.upload = upload.toString();
		
		System.out.println();
		System.out.println("------------------------------------------------------");
		System.out.println("Unfinished downloads: ");
		System.out.println(download);
		System.out.println("Unfinished uploads: ");
		System.out.println(upload);
		System.out.print("------------------------------------------------------");
		System.out.println();
		System.out.println("In order to resume transfer type 'download'/'upload' and corresponding number:");
		
	}
	
	public String findValue(String operation, String value){	
		String[] tab = null;
		if(operation.equals("download")) tab = download.split("\n");
		if(operation.equals("upload")) tab = upload.split("\n");
		for(String s : tab){
			if(s.startsWith(value)){
				return s;
			}
		} 
		return null;
	}
	
	public void resumeDownload(String host, String file){
		client.resumeDownload(host, file);
	}
	
	public void resumeUpload(String host, String file){
		client.resumeUpload(host, file);
	}
	
	public void start(){
		running = true;
	}

	public static void main(String[] args) {
		ClientConsole console = new ClientConsole("localhost", 32310);
		Scanner scanner = new Scanner(System.in);
		String number = "";
		String text = "";
		while(!console.running){}
		if(args.length > 0) {
			number = args[0];
		}
		if(number.equals("1")){
			System.out.println("In order to see available files type 'list':");
			while(console.running){
				if(scanner.nextLine().toUpperCase().equals("LIST")){
					console.getList();
				}
			}
		} else if(number.equals("2")){
			System.out.println("In order to download a file type host name and file name :: e.g., 1 file.txt");
			while(console.running){
				text = scanner.nextLine();
				try{
					String ID = text.split(" ")[0];
					Integer.parseInt(ID);
					console.downloadFile(text.split(" ")[0], text.split(" ")[1]);
				} catch(Exception e){
					System.out.println("Host's ID must be a number!");
				}
			}
		} else if(number.equals("3")){
			System.out.println("In order to upload a file type host name and file name :: e.g., 1 file.txt");
			while(console.running){
				text = scanner.nextLine();
				try{
					String ID = text.split(" ")[0];
					Integer.parseInt(ID);
					console.uploadFile(text.split(" ")[0], text.split(" ")[1]);
				} catch(Exception e){
					System.out.println("Host's ID must be a number!");
				}
			}
		} else if(number.equals("4")){
			System.out.println("In order to find unfinished transfers type 'resume':");
			while(console.running){
				text = scanner.nextLine();
				if(text.equals("resume")){
					console.getUnfinishedTransfers();
				} else if(text.startsWith("download")){
					console.resumeDownload(console.findValue("download", text.split(" ")[1]).split(" ")[3], 
							console.findValue("download", text.split(" ")[1]).split(" ")[1]);
				} else if(text.startsWith("upload")){
					console.resumeUpload(console.findValue("upload", text.split(" ")[1]).split(" ")[3], 
							console.findValue("upload", text.split(" ")[1]).split(" ")[1]);
				}
			}
		}
		scanner.close();
	}

}
