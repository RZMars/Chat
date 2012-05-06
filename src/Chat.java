import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Simple Chat Program
 * @author ginjih
 * ginjih.blogspot.com
 * twitter.com/ginjih
 */
public class Chat{
	public static void main( String args[] ){
		// メッセージ送信クライアント
		SocketClient cli = new SocketClient();
		// メッセージ受け取りサーバー
		SocketServer ser = new SocketServer();
		// ユーザー登録時のマルチキャスト受け取りサーバー
		MulticastServer mul = new MulticastServer();

		cli.start();
		ser.start();
		mul.start();
	}
}

class consts{
	/* ECHO_PORTはマルチキャストサーバーがListenしている。
	 * 自分がログインした場合、同一ネットの全ユーザーに登録情報が追加され、
	 * 別ユーザーが新規に参加した場合は、自分に登録情報がやってくる。
	 */
	public static final int ECHO_PORT = 10007;
	/* メッセージ LOGINは、別ユーザーがログイン情報を送ってきた事を示す*/
	public static final String LOGIN = "login";
	/* MESSAGE_PORTはユニキャストサーバーがLISTENしている。
	 * メッセージを受け取った場合と、別ユーザーログイン時に
	 * 別ユーザーに対してACKとして自分の情報を返す。
	 */
	public static final int MESSAGE_PORT = 10008;
	/* メッセージ ACKは、別ユーザーへログイン情報を送ることを示す。 */
	public static final String ACK = "ack";
	public static final int PACKET_SIZE = 1024;
	public static final String MCAST_ADDRESS = "224.0.0.1";
}
class IPTable{
	private static HashMap<String,String> table = new
		HashMap<String,String>();
	static void add( String uname,String ip){
		table.put( uname,ip );
	}
	static String get( String uname ){
		String ret = "";
		if ( table.containsKey( uname ) ){
			ret = table.get( uname );
		}else if ( table.containsValue(uname)){
			ret = uname;
		}
		return ret;
	}
	static boolean exists( String uname ){
		return table.containsKey(uname) || table.containsValue(uname);
	}
	// クライアントクラスから呼び出される。ストアデータをDump
	static void print(){
		System.out.println( "### Stored table List( ユーザー名->IPアドレス) ###" );
		for ( String uname: table.keySet() ){
			System.out.println( "\t" + uname + "-->" + table.get(uname ) );
		}
		System.out.println( "### End of List###" );
	}
}
class SocketClient extends Thread{
	public static String userName;
	
	// ストリームを用いた一行読み取り
	public String readln( ){
		String s = "";
		try{
			InputStreamReader in = new InputStreamReader(System.in);
			BufferedReader reader = new BufferedReader(in);
			s = reader.readLine();
		}catch (IOException e ){
			System.out.println( "[IOError] Keyboard" );
			System.exit(1);
		}
		return s;
	}
	public void run(){
		// Self IPAddress
		String ip = "";
		try{
			ip =
				InetAddress.getLocalHost().getHostAddress().toString();
			System.out.println( "【BOOT】メッセージ送信用クライアントを" +
					InetAddress.getLocalHost().getHostName()+
					"("+ip+")で起動しました。");
		}catch( UnknownHostException e){
		}
		System.out.println( "【INPUT】ユーザー名を入力してください。名前はオンラインユーザー全員に送信されます。" );
		userName = this.readln();
		// 自身をテーブルに追加
		IPTable.add( userName , ip );
		// ログイン情報をマルチキャストする。
		this.sendMulticast( consts.LOGIN, userName );

		String host = "";
		while (true){
			System.out.println(
					"【INPUT】接続先IPアドレスもしくはユーザー名を入力してください\n\t" +
					"listでユーザー一覧表示。.で["+host+"](前と同じ送信先)");
			String s = this.readln();
			// .でなければ新しいコマンドを代入
			if ( !s.equals("." ) ) host = s;
			if ( host.equals( "list") ){
				IPTable.print();
			}else{
				// コマンド
				if ( IPTable.exists(host)){
					host = IPTable.get(host);
					System.out.println( "->【CONV】Converted to " + host );
				}else{
					System.out.println(
							"->【CONV】Fail,スペルミスor新ユーザー?" );
				}
				System.out.println(
						"【INPUT】メッセージ。bで接続先ユーザーに戻る");
				String message = this.readln();
				if ( !message.equals("b") ){
					sendUnicast( userName, host , message);
				}
			}
		}
	}
	public void sendMulticast( String msg, String from ){
		MulticastSocket socket = null;

		try{
			InetAddress mcastAddress = InetAddress.getByName(
					consts.MCAST_ADDRESS);
			socket = new MulticastSocket();
			msg = msg + "<>" + from;
			byte[] bytes = msg.getBytes();
			DatagramPacket packet =
				new DatagramPacket( bytes, bytes.length , mcastAddress,
						consts.ECHO_PORT );
			socket.send(packet);
		}catch (IOException e ){
			System.out.println( "[IOError] Connection Failed?");
		}finally{
			if ( socket != null ){ socket.close();}
		}
	}
	public static void sendUnicast( String from, String to , String msg){
		Socket sock = null;
		PrintWriter writer = null;

		try{
			sock = new Socket();
			sock.connect( new InetSocketAddress (to ,consts.MESSAGE_PORT ) );
			
			writer = new PrintWriter(sock.getOutputStream() );
			writer.println( from + "<>" + msg );
			if ( writer != null ) {writer.flush();writer.close(); }
			if ( sock  != null ){sock.close();}
		}catch( IOException e ){
			System.out.println( "[IOError] Connection Failed?" );
		}
	}
}

class SocketServer extends Thread{
	public void run(){
		try{
			ServerSocket svsock = new ServerSocket(consts.MESSAGE_PORT);
			svsock.setSoTimeout(0); // Set to Infinity
			System.out.println(
					"【BOOT】メッセージ受信用サーバーを起動しました。(port=" + consts.MESSAGE_PORT+ ")" );

			while (true){
				Socket sock = svsock.accept();
				InputStreamReader in = new
					InputStreamReader(sock.getInputStream() );
				BufferedReader reader = new BufferedReader(in);

				while( sock.getInputStream().available() == 0 );

				String msgs ="", line;
				while ( (line = reader.readLine() ) != null ){
					msgs += line;
				}
				String[] msg = msgs.split("<>",2 );
				if ( msg[1].equals( consts.ACK) ){
					// ユーザー登録
					String ip = sock.getInetAddress().toString();
					System.out.println( 
							  "\t\t*[NOTICE]******\n" 
							+ "\t\t* [" + msg[0] + "@" + ip + "]"
							+ "がユーザーリストに登録されました。\n"
							+ "\t\t***************\n" );
				}else{
					System.out.println(
							  "\t\t* [MESSAGE]******\n"
							+ "\t\t* 送信元: [" + msg[0] + "@" +
							sock.getInetAddress() + "]\n"
							+ "\t\t* "+ msg[1]
							+ "\t\t****************\n" );
				}
				reader.close();
				sock.close();
			}
		}catch( SocketTimeoutException e1 ){
			System.out.println( "[Error ] TimedOut");
		}catch(IOException e2 ){
			System.out.println( "[IOError] on Message Server. still running?"
					);
		}
	}
}
class MulticastServer extends Thread{
	public void run(){
		MulticastSocket socket = null;
		byte[] buf = new byte[consts.PACKET_SIZE];
		DatagramPacket packet = new DatagramPacket(buf, buf.length) ;

		try {
			socket = new MulticastSocket(consts.ECHO_PORT );
			InetAddress mcastAddress = InetAddress.getByName(
					consts.MCAST_ADDRESS );
			socket.joinGroup( mcastAddress );
			System.out.println(
					"【BOOT】ステータス受け渡し用マルチキャストサーバーを起動しました。"+
					"(port = " +socket.getLocalPort() + ")" );
			while(true){
				socket.receive(packet);
				String message = new String(buf, 0 , packet.getLength() );
				String [] msg = message.split("<>" ,2  );

				System.out.println( "\t\t#[NOTICE] #########" );
				if ( msg[0].equals( consts.LOGIN ) ){
					String ip = packet.getAddress().toString().substring(1);
					System.out.println("\t\t#" + msg[0] + " " + msg[1] + "@" +
							ip + "が参加しました。" );
					System.out.println( "\t\t###########" );
					SocketClient.sendUnicast( SocketClient.userName, ip ,
							consts.ACK );
					IPTable.add( msg[1] , ip );
				}
			}
		}catch( IOException e ){
			System.out.println("[IOError] on Broadcast Server");
		}finally{
			if ( socket != null ){ socket.close(); }
		}
	}
}
