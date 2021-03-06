import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.io.IOUtils;

import com.healthmarketscience.rmiio.RemoteInputStream;
import com.healthmarketscience.rmiio.RemoteInputStreamClient;
import com.healthmarketscience.rmiio.RemoteOutputStream;
import com.healthmarketscience.rmiio.RemoteOutputStreamClient;

public class ChatServiceImpl extends UnicastRemoteObject implements ChatService {

	private static final ChatObservable CHAT_OBSERVABLE = new ChatObservable();
    private Connection connection;
    private ArrayList<Compte> comptes = new ArrayList<>();

    protected ChatServiceImpl() throws RemoteException {
        super();
        connection = getDatabaseConnection();
        if (connection == null) {
            System.out.println("Can't connect to the database");
        }
        else {
            System.out.println("Connected to the database");
            boolean received = getAllUsers();
            if (received) {
                System.out.println("SELECT query finished successfully");
            }
            for (Compte compte : comptes) {
                System.out.println(compte.toString());
            }
        }

//        // on definit un timer pour actualiser les infos
//        TimerTask repeatedTask = new TimerTask() {
//            public void run() {
//                System.out.println("Affichage des clients connectee : ");
//                System.out.println("-----------");
//                for (int i = 0; i < comptes.size(); i++) {
//                    Compte c = comptes.get(i);
//                    if (c.isStatus() && ((new Date().getTime() - c.getDate().getTime()) / 1000 > 1))
//                        c.setStatus(false);
//                    if (c.isStatus())
//                        System.out.println(c.getPseudo());
//                }
//                System.out.println("-----------");
//            }
//        };
//        Timer timer = new Timer("Timer");
//        timer.scheduleAtFixedRate(repeatedTask, 0, 1000);
    }

    @Override
    public int sent(ArrayList list) throws RemoteException {
        String pseudo = (String)list.get(1);
        if((int)list.get(0) == 0) { // Demande d'inscription
            String email = (String) list.get(3);
            if (searchByPseudo(pseudo) != null)
                return 1;
            else if (searchByEmail(email) != null)
                return 2;
            else {
                // Ajout du nouvel utilisteur a la liste de comptes
                //Compte c = new Compte(pseudo, (String)list.get(2), email, true);
                Compte c = new Compte(pseudo, email, (String) list.get(5),(String) list.get(4), (String)list.get(2), true);
                registerNewUser(c);
                comptes.add(c);
            }
        } else if ((int) list.get(0) == 1) { // Demande de connexion
            String password = (String) list.get(2);
            Compte c = searchByPseudo(pseudo);
            if (c == null)
                return 3;
            else if (!c.getPassword().equals(password))
                return 4;
            else {
                // mis a jour de l'etat de l'utilisateur
                c.setDate();
            }
        } else if ((int) list.get(0) == 2) { // Confirmation de presence
            Compte c = searchByPseudo(pseudo);
            c.setDate();
        } else { // Deconnexion
            Compte c = searchByPseudo(pseudo);
            c.setStatus(false);
        }
        return 0;
    }

    private Compte searchByEmail(String email) {
        Iterator<Compte> it = this.comptes.iterator();
        while (it.hasNext()) {
            Compte c = it.next();
            if (c.getEmail().equals(email))
                return c;
        }
        return null;
    }

    private Compte searchByPseudo(String pseudo) {
        Iterator<Compte> it = this.comptes.iterator();
        while (it.hasNext()) {
            Compte c = it.next();
            if (c.getPseudo().equals(pseudo))
                return c;
        }
        return null;
    }

    public ArrayList<Compte> getComptes() {
        return this.comptes;
    }

    // connect to the database file
    private Connection getDatabaseConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("Driver loaded");
            Connection connection = DriverManager.getConnection("jdbc:sqlite:database.db");
            System.out.println("Connected Successfully");
            return connection;
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // registers a user in the database and returns true if succeeded or false if not
    private boolean registerNewUser(Compte compte) {
        try {
            Statement statement = connection.createStatement();
            String query = "INSERT INTO USERS " + "(username, email, firstname, familyname, password) "
                + "VALUES('"+compte.getPseudo()+"', '" + compte.getEmail() + "', '" + compte.getFirstname() +"', '" + compte.getFamilyname() +"', '" + compte.getPassword() +"')";
            statement.executeUpdate(query);
            System.out.println("User registered successfully");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // executes SELECT * FROM USERS and add every user to the ArrayList comptes then sends true if succeeded or false if not
    private boolean getAllUsers() {
        try{
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM USERS");
            ResultSet resultSet = statement.executeQuery();

            while(resultSet.next()) {
                comptes.add(new Compte(resultSet.getString("username"), resultSet.getString("email"), resultSet.getString("firstname"), resultSet.getString("familyname"), resultSet.getString("password"), false));
            }
            System.out.println("User registered successfully");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    
	@Override
	public boolean sendTextTo(String sender, String receiver, String text) throws RemoteException {
		return CHAT_OBSERVABLE.sendTextTo(sender, receiver, text);
	}
	
	@Override
	public boolean addChatObserver(ChatObserver chatObserver) throws RemoteException {
		return CHAT_OBSERVABLE.addChatObserver(chatObserver);
	}

	@Override
	public boolean updateOnlineUsers() throws RemoteException {
		return CHAT_OBSERVABLE.updateOnlineUsers();
	}

	@Override
	public boolean removeChatObserver(ChatObserver chatObserver) throws RemoteException {
		return CHAT_OBSERVABLE.removeChatObserver(chatObserver);
	}
	//TODO
	@Override
	public boolean sendFile(String sender, String receiver, String filename,RemoteInputStream inputFile) throws RemoteException {
	    InputStream inputStream = null;
		try {
			inputStream = RemoteInputStreamClient.wrap(inputFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	 
		String newFilename = System.currentTimeMillis()+filename.substring(filename.lastIndexOf('.'));
    	File file = new File(newFilename);
    	OutputStream outputStream = null;
		try{
			outputStream = new FileOutputStream(file);
			IOUtils.copy(inputStream, outputStream);
		} catch (IOException e) {
			// handle exception here
		}
		finally {
			try {
				outputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Size: "+file.length());
		return CHAT_OBSERVABLE.sendFileTo(sender, receiver, newFilename);
	  }
	
	
	@Override
	public boolean sendFileToGroup(String sender, String receiver, String filename,RemoteInputStream inputFile) throws RemoteException {
	    InputStream inputStream = null;
		try {
			inputStream = RemoteInputStreamClient.wrap(inputFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	 
		String newFilename = System.currentTimeMillis()+filename.substring(filename.lastIndexOf('.'));
    	File file = new File(newFilename);
		OutputStream outputStream = null;
		try{
			outputStream = new FileOutputStream(file);
			IOUtils.copy(inputStream, outputStream);
		} catch (IOException e) {
			// handle exception here
		}
		finally {
			try {
				outputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Size: "+file.length());
		Group group = CHAT_OBSERVABLE.getGroup(receiver);
		for(String participant:group.getParticipants()) {
			if(!sender.equals(participant))
				CHAT_OBSERVABLE.sendFileTo("#"+group.getName()+"#"+sender, participant, newFilename);
		}
		return true;
	  }
	
	@Override
	public boolean getFile(RemoteOutputStream outputFile, String filename) throws RemoteException {
	    OutputStream outputStream = null;
		try {
			outputStream = RemoteOutputStreamClient.wrap(outputFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	 
	    File file = new File(filename);
	    
	    InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(file);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	    try {
			IOUtils.copy(inputStream, outputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			try {
				outputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	    
	    return true;
	}

	@Override
	public void addGroup(Group group) throws RemoteException {
		CHAT_OBSERVABLE.add(group);
	}

	@Override
	public void sendTextToGroup(String username, String receiver, String textMessage) throws RemoteException {
		Group group = CHAT_OBSERVABLE.getGroup(receiver);
		for(String participant:group.getParticipants()) {
			if(!username.equals(participant))
				sendTextTo("#"+group.getName()+"#"+username, participant, textMessage);
		}
	}

	@Override
	public ArrayList<Group> getAllgroups() throws RemoteException {
		return this.CHAT_OBSERVABLE.getAllGroups();
	}
	
	@Override
	public Compte getCompte(String user) throws RemoteException {
		return searchByPseudo(user);
	}

	@Override
	public Group getGroup(String groupName) throws RemoteException {
		return CHAT_OBSERVABLE.getGroup(groupName);
	}
}
