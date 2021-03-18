package ch.epfl.javass;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ch.epfl.javass.gui.GraphicalPlayerAdapter;
import ch.epfl.javass.jass.JassGame;
import ch.epfl.javass.jass.MctsPlayer;
import ch.epfl.javass.jass.PacedPlayer;
import ch.epfl.javass.jass.Player;
import ch.epfl.javass.jass.PlayerId;
import ch.epfl.javass.net.RemotePlayerClient;
import ch.epfl.javass.net.StringSerializer;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * @author GARANDEL.Eloi (SCIPER : 300326) / D'ETERNOD.Kilian (SCIPER : 296357) 
 */
public class LocalMain extends Application{

	// time to wait in milliseconds
	public static final double SIM_TIME = 2;
	public static final int TURN_TIME = 1000;
	
	// default settings for simulated and remote players
	public static final int DEFAULT_ITERATIONS = 10000;
	public static final String DEFAULT_HOST = "localhost";
	
	private final String[] defaultNames = {"Aline", "Bastien", "Colette", "David"};
	
	private final Map<PlayerId, Player> players = new HashMap<>();
    private final Map<PlayerId, String> playerNames = new HashMap<>();
    private Random mainRandom;
    private long gameSeed;
	
    /**
     * Main to launch a local game with the given arguments
     */
	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
	public void start(Stage primaryStage) throws Exception {
		List<String> args = getParameters().getRaw();
		
		if(args.size() < PlayerId.COUNT || args.size() > PlayerId.COUNT+1) {
			errorToDo("Error : wrong number of arguments");
		}
	    
		// seed argument processing (if present) for the main random generator
	    if(args.size() > PlayerId.COUNT) {
			try {
				mainRandom = new Random(Long.parseLong(args.get(4)));
			} catch(NumberFormatException e) {
				errorToDo("Error : wrong seed");
			}
		} else { mainRandom = new Random(); }
	    
	    gameSeed = mainRandom.nextLong();
	    
	    // player arguments processing
		for(int i = 0; i < PlayerId.COUNT; i++) {
			PlayerId actual = PlayerId.ALL.get(i);
			String[] s = StringSerializer.split(":", args.get(i));

			switch(s[0]) {
			
			case "h" :  // for human playing on this computer
				createHumanPlayer(actual, s);
				break;
			
			case "s" : // for simulated player
				if(s.length==3) {
					int iterations = 0;
					
					try {
						iterations = Integer.parseInt(s[2]);
					} catch (NumberFormatException e) {
						errorToDo("Error : invalid iterations for online player");
					}
					
					if(iterations<10) {
						errorToDo("Error : iterations must be greater than 10");
					}
				}
				createSimulatedPlayer(actual, s);
				break;
				
			case "r" : // for remote player playing on another player
				createRemotePlayer(actual, s);
				break;
			
			default : //default in case it was wrongly launched
				errorToDo("Error : incorrect arguments");
				break;
			}
		}

		// thread of the actual game
		Thread gameThread = new Thread(() -> {
			JassGame jassGame = new JassGame(gameSeed, players, playerNames);

			while (!jassGame.isGameOver()) {
				jassGame.advanceToEndOfNextTrick();
				try {
					Thread.sleep(TURN_TIME);	// wait between each turn
				} catch (InterruptedException e) {}
			}
		});
		gameThread.setDaemon(true);
		gameThread.start();
	}
	
	/**
	 * Separate method to create a human player
	 * @param pId	PlayerId of the player to create
	 * @param s		arguments (length 1-2, h:name, if no name given chosen by default)
	 */
	private void createHumanPlayer(PlayerId pId, String[] s) {
		
		switch(s.length) {
		
		case 1 :
			players.put(pId, new GraphicalPlayerAdapter());
			playerNames.put(pId, defaultNames[pId.ordinal()]);
			break;
			
		case 2 : 
			players.put(pId, new GraphicalPlayerAdapter());
			playerNames.put(pId, s[1]);
			break;
		
		default :	
			errorToDo("Error : incomplete IRL player");
		}
	}
	
	/**
	 * Separate method to create a simulated player
	 * @param pId	PlayerId of the player to create
	 * @param s		arguments (length 1-3, h:name:iterations, if no name or/and iterations given chosen by default)
	 */
	private void createSimulatedPlayer(PlayerId pId, String[] s) {
		
		switch(s.length) {
		
		case 1 :
			players.put(pId, new PacedPlayer(
					new MctsPlayer(pId, mainRandom.nextLong(), DEFAULT_ITERATIONS),
					SIM_TIME));
			playerNames.put(pId, defaultNames[pId.ordinal()]);
			break;
			
		case 2 : 
			players.put(pId, new PacedPlayer(
					new MctsPlayer(pId, mainRandom.nextLong(), DEFAULT_ITERATIONS),
					SIM_TIME));
			playerNames.put(pId, s[1]);
			break;
			
		case 3 : 
			players.put(pId, new PacedPlayer(
					new MctsPlayer(pId, mainRandom.nextLong(), Integer.parseInt(s[2])),
					SIM_TIME));
			playerNames.put(pId, s[1]);
			break;
			
		default : 
			errorToDo("Error : incomplete simulated player");
			
		} // wrapped in PacedPlayer so that the simulated player always take the same time to play
	}
	
	/**
	 * Separate method to create a remote player
	 * @param pId	PlayerId of the player to create
	 * @param s		arguments (length 1-3, h:name:host, if no name or/and host given chosen by default)
	 * @throws		IOException if remote player unreachable
	 */
	private void createRemotePlayer(PlayerId pId, String[] s) {
		try {
			
			switch(s.length) {
				
			case 1 :
				players.put(pId, new RemotePlayerClient(DEFAULT_HOST));
				playerNames.put(pId, defaultNames[pId.ordinal()]);
				break;
				
			case 2 : 
				players.put(pId, new RemotePlayerClient(DEFAULT_HOST));
				playerNames.put(pId, s[1]);
				break;
				
			case 3 : 
				players.put(pId, new RemotePlayerClient(s[2]));
				playerNames.put(pId, s[1]);
				break;
				
			default : 
				errorToDo("Error : incomplete online player");
			}
			
		} catch (IOException e) {
			System.err.println("Error : can't connect to distant player");
			System.exit(2);
		}
	}
	
	/**
	 * Private method that stop the program and show why it fails to the user
	 * @param s error message that tell what's the reason of the failure
	 */
	private void errorToDo(String s) {
		System.err.println(s + "\n");
		System.out.println("How to use : java ch.epfl.javass.LocalMain <j1> <j2> <j3> <j4> ([<seed>]) où :");
		System.out.println("<jn> specify player #n, as well as : ");
		System.out.println("h(:<name>) IRL player named <name>");
		System.out.println("s(:<name>:<iterations>)  simulated plan named <name> that will test <iterations> rounds before playing its card");
		System.out.println("r(:<name>:<host>)  online player named <name> logging from address <host>");
		System.out.println("[<seed>] seed that defines randomness of the game (value is a [long])");
		System.exit(1);
	}
	
	
}
