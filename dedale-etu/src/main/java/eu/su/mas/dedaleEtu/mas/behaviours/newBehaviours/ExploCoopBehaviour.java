package eu.su.mas.dedaleEtu.mas.behaviours.newBehaviours;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.graphstream.graph.Node;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.GsLocation;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.TreasureInfo;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;


/**
 * Explore the map while collaborating with other agents.
 */
public class ExploCoopBehaviour extends SimpleBehaviour {

	private boolean wait = true;
	private int waitTime = 100;	// time between two actions

	private boolean waitTimings = true;	//NE PAS TOUCHER, EXPERIMENTAL

	private static final long serialVersionUID = 8567689731496787661L;

	private boolean finished = false;
	
	private boolean setupFlag = false;	// setup is false before the first action(), true after the first action()

	private boolean verbose = false;
	private boolean mapVerbose = false;
	private boolean siloKnowledgeVerbose = false;
	private boolean randomGoalsVerbose = false;
	private boolean moveRequestVerbose = false;
	private boolean optimalSiloNodeVerbose = false;
	private boolean treasureVerbose = false;
	private boolean siloStrategyVerbose = false;
	private boolean communicateWithSiloVerbose = false;
	private boolean missionVerbose = false;
	private boolean treasureInteractionsVerbose = false;

	
	// time to wait before communicating with an agent again (not used by deadlock resolutions)
    private long timeBetweenMessages = waitTimings ? 300 : 30;	// 1 second ?
    
    // used when forcing comincations with silo (checkForCommunicationAlt())
    private long timeBetweenMessagesAlt = waitTimings ? 1000 : 100;	// 1 second

    // time to wait before sending a deadlock message again to the same agent with the same content
	private long timeDeadlockTreshold = waitTimings ? 200 : 20;	// 0 seconds is fastest but generates a lot more messages

	// SILO
	// time to wait before trying to find a new position for the SILO
	private int timeBetweenSiloPositionUpdates = waitTimings ? 20000 : 2000;
	
	private int timeBetweenPhase3_1 = waitTimings ? 25000 : 2500;
	
	private int obstaclesExpirationTime = waitTimings ? 5000 : 500;
	
	
	/*
	 * whether the agent is stationary or not
	 * Deadlocks are handled differently if the agent is stationary (lower priority)
	 */
	private boolean isStationary = false;
	
	/**
	 * Current knowledge of the agent regarding the environment
	 */
	private MapRepresentation myMap;

	/**
	 * list of maps representations of the agents (one map per agent)
	 * ie : what the agent believes the other agents' maps are
	 */
	// List<Couple<String, MapRepresentation>> agentMaps;
	HashMap<String, MapRepresentation> agentMaps;
	
	/*
	 * list of timestamps since last communication for each agent (not updated by deadlock resolutions)
	 */
	HashMap<String, Long> lastCommunicationTime;
    //HashMap<String, Integer> updatesSinceLastCommunication;
    

	/**
	 * list of current communications (agentName, behaviour associated)
	 */
	List<Couple<String, Behaviour>> currentCommunications;

	/**
	 * list of agent names to share the map with
	 */
	private List<String> list_agentNames;
	
	/**
	 * current phase
	 * 1 : exploration
	 * 2 : exploitation (agent)
	 * 3 : exploitation (SILO)
	 */
	private int phase;
	
	/*
	 * phase of other agents
	 */
	private HashMap<String, Integer> agentPhases;
	
	/*
	 * main destination the agent is trying to get to
	 */
	private String goalDestination;
	
	/*
	 * main destination priority
	 */
	private int goalPriority;
	
	/*
	 * temporary destination of the agent, used for instance when resolving a deadlock
	 */
	private String temporaryDestination;
	
	/*
	 * temporary destination priority
	 */
	private int temporaryPriority;
	
	/*
	 * List of moves to do next to get to the current destination
	 */
	private List<String> pathToDestination;

	/*
	 * Last message related to deadlock resolution that was sent
	 * {"recipient" : agentName, "time" : date, "content" : List<String>}
	 */
	private HashMap<String, Object> lastDeadlockMessage;
	
	/*
	 * List of known treasures
	 */
	private HashMap<String, TreasureInfo> knownTreasures;
	
	/*
	 * List of golem positions seen
	 * {
	 * "position" : {
	 *         "type" : str,		// "Golem"
	 *         "timestamp" : long
	 *         }
	 * }
	 */
	private HashMap<String, HashMap<String, Object>> golemSightings = new HashMap<String, HashMap<String, Object>>();
	
	/*
	 * true if the agent is a SILO agent, false if it's a normal agent
	 */
	private boolean isSiloAgent;
	
	/*
	 * What we know about the SILO, will be shared with the other agents
	 * {
	 *         "timestamp" : long,
	 *         "positionStatus" : int,
	 *         "position" : str,
	 *         "nextPosition" : str
	 * }
	 */
	private HashMap<String, Object> siloKnowledge = null;
	
	/*
	 * Keep track of what each agent knows about the SILO, used to communicate with other agents our knowledge if theirs is outdated
	 * {
	 * "agentName" : {
	 *         "timestamp" : long,
	 *         "positionStatus" : int,		0 | 1 | 2 | 3
	 *         "position" : str,			(-1) if positionStatus is 0 
	 *         "nextPosition" : str			(-1) if positionStatus is 0 or 1 or 2
	 *         }
	 * }
	 */
	private HashMap<String, HashMap<String, Object>> SiloKnowledgeVector = new HashMap<String, HashMap<String, Object>>();

	private long lastSiloInteractionTimeStamp = 0;	// last time the agent interacted with the SILO
	private int considerSiloLostAfter = waitTimings ? 20000 : 2000;	// if we don't see the SILO for this long, we consider it lost
	private boolean considerSiloLost = false;
	
	/*
	 * - "goToSilo"
	 * - "findSilo"
	 * - "explore"
	 * - "waitForSiloMessage"
	 * - "exploreOpenNodes"
	 * - "unlockTreasure"
	 * - "pickUpTreasure"
	 * - "unlockAndPickUpTreasure"
	 */
	private String missionType = null;
	
	/*
	 * destinations of the mission (any of them are valid)
	 */
	private List<String> missionDestinations = null;
	
	/*
	 * timeout of the mission
	 */
	private long missionTimeout = 0;
		
	
	/*
	 * SILO ATTRIBUTES :
	 */
	
	/*
	 * the optimal position of the SILO
	 */
	private String correctSiloPosition = null;
	
	/*
	 * Status of the SILO position
	 * 0 : no position set yet (Exploration Phase)
	 * 1 : in transit to new position (don't bother stopping for communications, getting to the new destination is the priority)
	 * 2 : currently set at a the correct position
	 * 3 : about to change to a new position (we are waiting for all agents to be aware of the next position before moving)
	 */
	private int siloPositionStatus = 0;
	
	/*
	 * last time the SILO position was updated
	 * updated only once every 20 seconds
	 */
	private long lastSiloPositionUpdateTime = 0;
	
	/*
	 * flag to avoid recalculating the optimal SILO position once all nodes are closed
	 */
	private boolean finalSiloPositionFoundFlag = false;
	
	/*
	 * list of agents to notify of the new SILO position
	 */
	private List<String> agentsToNotifyOfNewSiloPosition = new ArrayList<String>();
	
	/*
	 * time since we went to phase 3
	 * used to force the switch to the new SILO position even if not all agents were notified if 25 seconds have passed
	 */
	private long phase3Time = 0;
	
	
	
	
/**
 * @param myagent reference to the agent we are adding this behaviour to
 * @param myMap known map of the world the agent is living in
 * @param agentNames name of the agents to share the map with
 */
	public ExploCoopBehaviour(final AbstractDedaleAgent myagent, MapRepresentation myMap, List<String> agentNames, boolean isSiloAgent) {
		super(myagent);
		this.list_agentNames = agentNames;
		this.myMap = myMap;
		this.agentMaps = new HashMap<String, MapRepresentation>();
		this.lastCommunicationTime = new HashMap<>();
		// this.updatesSinceLastCommunication = new HashMap<String, Integer>();
		this.currentCommunications = new ArrayList<Couple<String, Behaviour>>();
		this.agentPhases = new HashMap<String, Integer>();
		this.knownTreasures = new HashMap<String, TreasureInfo>();
		this.isSiloAgent = isSiloAgent;
	}

	/*
	 * Get the color of an agent, use the alternative color if needed
	 * TIM : Orange -> LightSalmon
	 * ELSA : Cyan -> LightSkyBlue
	 * JOHN : Green -> LightGreen
     */
	private String getColor(String agentName, Boolean alternative) {
		if (agentName.equals("Tim") || agentName.equals("Agent1")) {
			if (!alternative) {
//				return "Orange";
				return "#FFCC99";
			} else {
//				return "LightSalmon";
				return "#FFE4E1";
			}
		} else if (agentName.equals("Elsa")) {
			if (!alternative) {
//				return "Cyan";
				return "#77FFFF";
			} else {
//				return "LightSkyBlue";
				return "#DDFFFF";
			}
		} else if (agentName.equals("John")) {
			if (!alternative) {
//                return "Green";
				return "#77FF77";
            } else {
//                return "LightGreen";
            	return "#DDFFDD";
            }
        } else {
            if (!alternative) {
//                return "Gray";
            	return "#DDDDDD";
            } else {
//                return "White";
            	return "#FFFFFF";
            }
        }
	}
	
	/*
	 * called the first time action() is called
	 */
	private void setup() {
		// 1) create the map if it doesn't exist
        if (this.myMap == null) {
        	String color = "graph {"+"fill-color: "+getColor(this.myAgent.getLocalName(), false)+";"+"}";
			this.myMap = new MapRepresentation(color);
			//this.myMap = new MapRepresentation(false);	// use this to disable the GUI
			
        }

        // 2) initialize the agentMaps list
        for (String agentName : this.list_agentNames) {
			if (verbose) {
				System.out.println("Adding agent " + agentName + " to the agentMaps list of " + this.myAgent.getLocalName());
			}
        	String color = "graph {"+"fill-color: "+getColor(this.myAgent.getLocalName(), true)+";"+"}";
        	//this.agentMaps.put(agentName, new MapRepresentation(color));
        	this.agentMaps.put(agentName, new MapRepresentation(false));	// use this to disable the GUI
        }
        
        // 3) initialize the siloKnowledge for the SILO (the only one to know by default)
		if (this.isSiloAgent) {
			this.setSiloKnowledge(System.currentTimeMillis(), 0, "-1", "-1");
		}
        
        // 3) initialize the Silo knowledge vector of the other agents
        this.SiloKnowledgeVector = new HashMap<String, HashMap<String, Object>>();
		for (String agentName : this.list_agentNames) {
			this.SiloKnowledgeVector.put(agentName, null);
		}
        
        // 3) initialize the updatesSinceLastCommunication list
		for (String agentName : this.list_agentNames) {
			this.lastCommunicationTime.put(agentName, System.currentTimeMillis());
			//this.updatesSinceLastCommunication.put(agentName, 0);
		}
        
        // 4) add the behaviours to receive maps
        this.myAgent.addBehaviour(new ReceiveMapBehaviour(this.myAgent, this, mapVerbose));
        
        // 4) add the behaviours to receive SILO knowledge
        this.myAgent.addBehaviour(new ReceiveSiloKnowledgeBehaviour(this.myAgent, this, siloKnowledgeVerbose));

        // 4) add the behaviours to receive missions
        this.myAgent.addBehaviour(new ReceiveMissonsBehaviour(this.myAgent, this, missionVerbose));
        
        // 5) initialize phase to 1
        this.phase = 1;
		for (String agentName : this.list_agentNames) {
			this.agentPhases.put(agentName, 1);
		}
		
		// 6) add the behaviours to receive moveRequests
		this.myAgent.addBehaviour(new ReceiveMoveRequestsBehaviour(this.myAgent, this, moveRequestVerbose));

		// 6.5) TODO: testing : add the SiloStrategyBehaviour immediately
		if (this.isSiloAgent) {
			this.myAgent.addBehaviour(new SiloStrategyBehaviour(this.myAgent, this, list_agentNames, siloStrategyVerbose));
		}
		
        // 7) make sure setup() is not called again
        this.setupFlag = true;
    }

	/* 
	 * get the supposed position of the silo for obstacles
	 */
	public String getSiloPosition() {
		// 1) if we don't have any silo knowledge, return null
		if (this.siloKnowledge == null) {
			return null;
		}
		
		// 2) if the silo positionStatus is 0, we don't know the position of the silo
		if (this.siloKnowledge.get("positionStatus") == null) {
			return null;
		}
		
		// 3) return the supposed position
		return (String) this.siloKnowledge.get("position");
	}
	
	/*
	 * returns the supposed obstacles on the map
	 */
	public List<String> getObstacles() {
		List<String> obstaclesList = new ArrayList<String>();

		// 1) get the golem positions
		for (String obstacle : this.golemSightings.keySet()) {
			obstaclesList.add(obstacle);
		}
		
		// 2) get the Silo position
		if (! this.isSiloAgent) {
			String siloPosition = getSiloPosition();
			if (siloPosition != null) {
				obstaclesList.add(siloPosition);
			}
		}
		
		return obstaclesList;
	}
	
	/*
	 * update the golem sightings
	 */
	public void updateGolemSightings(List<String> golems) {
		// 1) remove the sightings that are expired
		long now = System.currentTimeMillis();
		List<String> toRemove = new ArrayList<String>();
		for (String position : this.golemSightings.keySet()) {
			HashMap<String, Object> info = this.golemSightings.get(position);
			if (info.get("timestamp") != null) {
				long timestamp = (long) info.get("timestamp");
				if ((now - timestamp) > this.obstaclesExpirationTime) {
					toRemove.add(position);
				}
			}
		}
		for (String position : toRemove) {
			this.golemSightings.remove(position);
		}
		
		// 2) add the new sightings
		for (String sighting : golems) {
			HashMap<String, Object> info = new HashMap<String, Object>();
			info.put("type", "Golem");
			info.put("timestamp", now);
			this.golemSightings.put(sighting, info);
		}
	}
	
	/*
	 * Get the current position of the agent
	 */
	public Location getPosition() {
		return ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();
	}
	
	/*
	 * Get the observations from the current position
	 * returns a hashmap with the list of observations and the list of agents in the observable area
	 */
	private HashMap<String, Object> getObservations() {
		HashMap<String, Object> observations = new HashMap<String, Object>();
		
		//1) get the list of observations from the current position
		List<Couple<Location, List<Couple<Observation, String>>>> lobs = ((AbstractDedaleAgent) this.myAgent).observe();

		//2) get the list of agents in the observable area
		List<String> visibleAgents = new ArrayList<String>();
		List<String> visibleGolems = new ArrayList<String>();
		// for each observable location :
		for (Couple<Location, List<Couple<Observation, String>>> l : lobs) {
			// for each thing observable at the location :
			for (Couple<Observation, String> o : l.getRight()) {
				// if the observable thing is an agent :
				if (o.getLeft().toString().equals("AgentName")) {
					// add the agent to the list of currently observable agents if it part of the agent list
					String agentName = o.getRight();
					if (list_agentNames.contains(agentName)) {
						visibleAgents.add(o.getRight());
					}
					else {
						// golem
						visibleGolems.add(o.getRight());
					}
				}
			}
		}
		
		// 3) get the list of obstacles in the observable area
		List<String> obstacles = new ArrayList<String>();
		// for each observable location :
		for (Couple<Location, List<Couple<Observation, String>>> l : lobs) {
			// for each thing observable at the location :
			for (Couple<Observation, String> o : l.getRight()) {
				// if the observable thing is an obstacle (ie : an agent or a golem)
				if (o.getLeft().toString().equals("AgentName")) {
					// add the obstacle to the list of currently observable obstacles
					obstacles.add(l.getLeft().getLocationId());
				}
			}
		}
//		
//		// 4) get the list of golems in the observable area
//		
//		// for each observable location :
//		for (Couple<Location, List<Couple<Observation, String>>> l : lobs) {
//			// for each thing observable at the location :
//			for (Couple<Observation, String> o : l.getRight()) {
//				// if the observable thing is a golem :
//				if (o.getLeft().toString().equals("Golem")) {
//					// add the golem to the list of currently observable golems
//					visibleGolems.add(l.getLeft().getLocationId());
//				}
//			}
//		}
		
		// 3) return the observations
		// put the observations in the hashmap
		observations.put("lobs", lobs);
		observations.put("visibleAgents", visibleAgents);
		observations.put("obstacles", obstacles);
		observations.put("visibleGolems", visibleGolems);
		return observations;
	}
	
	/*
	 * Returns true if the other agent's knowledge is outdated compared to ours
	 * true : would mean that we need to send our knowledge to the other agent
	 * false : would mean that we don't need to send our knowledge to the other agent
	 */
	public boolean otherAgentSiloKnowledgeIsOutdated(HashMap<String, Object> ourAgentKnowledge, HashMap<String, Object> otherAgentKnowledge) {
		// 1) if our knowledge is null, it is not outdated (we don't know anything about the SILO)
		if (ourAgentKnowledge == null) {
			return false;
		}
		
		// 2) if the other agent's knowledge is null, it's outdated (we know more than him because he knows nothing)
		if (otherAgentKnowledge == null) {
			return true;
		}
		
		// 3) if the other agent's timestamp is newer than ours, it's not outdated (it might be more up to date than ours)
		if ((Long) otherAgentKnowledge.get("timestamp") > (Long) ourAgentKnowledge.get("timestamp")) {
			return false;
		}
		
		// 4) the other agent's timestamp is older than ours, we need to check if the contents are the same
		if (otherAgentKnowledge.get("positionStatus").equals(ourAgentKnowledge.get("positionStatus"))
				&& otherAgentKnowledge.get("position").equals(ourAgentKnowledge.get("position"))
				&& otherAgentKnowledge.get("nextPosition").equals(ourAgentKnowledge.get("nextPosition"))) {
			// the contents are the same, it's not outdated
			return false;
		}
		// 5) the contents are different, it's outdated
		return true;
	}
	
	/*
	 * Check if we want to communicate the map with the given agent,
	 * if we want to communicate : create the corresponding behaviour and return it
	 * else : return null
	 */
	private Behaviour checkForMapCommunication(String agentName) {
		// 1) if we are already communicating the map with the agent, return
		for (Couple<String, Behaviour> couple : this.currentCommunications) {
			if (couple.getLeft().equals(agentName)) {
				if (couple.getRight() instanceof ShareMapBehaviour) {
					//System.out.println("Already communicating the map with " + agentName);
					return null;
				}
			}
		}
		
		// 2) check if the map to share is different from the one we have
		MapRepresentation agentMap = this.agentMaps.get(agentName);
		if (agentMap == null) {
			// System.out.println("Error: agentMap is null");
			return null;
		}
		if (this.myMap.equals(agentMap)) {
			// System.out.println("The map to share is the same as the one we have, not sharing it");
			return null;
		}
			
		// 3) create a behaviour to share the map with the agent
		HashMap<String, Object> content = new HashMap<String, Object>();	// additional content of the message (my position)
		content.put("position", getPosition().getLocationId());
		content.put("treasures",  new HashMap<>(this.knownTreasures));
		ShareMapBehaviour shareMapBehaviour = new ShareMapBehaviour(this.myAgent, agentName, this.myMap, this, content, mapVerbose);
		
		// 4) return the behaviour
        return shareMapBehaviour;
	}
	
	/*
	 * Check if we want to communicate the SILO status with the given agent, if we
	 * want to communicate : create the corresponding behaviour and return it else :
	 * return null
	 */
	private Behaviour checkForSiloCommunication(String agentName) {
		// 1) if we are already communicating the SILO status with the agent, return
		for (Couple<String, Behaviour> couple : this.currentCommunications) {
			if (couple.getLeft().equals(agentName)) {
				if (couple.getRight() instanceof ShareSiloKnowledgeBehaviour) {
					// System.out.println("Already communicating the SILO status with " +
					// agentName);
					return null;
				}
			}
		}

		// 2) check if the other agent's knowledge is outdated
		HashMap<String, Object> otherAgentKnowledge = this.SiloKnowledgeVector.get(agentName);
		if (!otherAgentSiloKnowledgeIsOutdated(this.siloKnowledge, otherAgentKnowledge)) {
			// 2.1) the other agent's knowledge is not outdated, return
			return null;
		}

		// 3) create a behaviour to share the SILO status with the agent
		HashMap<String, Object> additionalContent = new HashMap<String, Object>(); // none currently
		ShareSiloKnowledgeBehaviour shareSiloKnowledgeBehaviour = new ShareSiloKnowledgeBehaviour(this.myAgent, agentName, this.siloKnowledge, this, additionalContent, siloKnowledgeVerbose);

		// 4) return the behaviour
		return shareSiloKnowledgeBehaviour;
	}
	
	/*
	 * Check if there is a visible agent we want to communicate with and add the corresponding behaviour
	 * Communications checked are :
	 * - share the map/treasure with the agent
	 * - share the SILO status with the agent
	 */
	private void checkForCommunication(List<String> visibleAgents) {
		// for each visible agent :
		for (String agentName : visibleAgents) {
			// 1) if the timestamp since the last communication is too recent (now - lastCommunicationTime < timeTreshold), return
			long now = System.currentTimeMillis();
			long lastCommunication = this.lastCommunicationTime.get(agentName);
			if ((now - lastCommunication) < this.timeBetweenMessages) {
				continue;
			}

			// 2) check if we need to communicate the map with the agent
			Behaviour shareMapBehaviour = checkForMapCommunication(agentName);
			
			if (shareMapBehaviour != null) {
				// 2.1) add the behaviour to the communication list
				Couple<String, Behaviour> couple = new Couple<String, Behaviour>(agentName, shareMapBehaviour);
				this.currentCommunications.add(couple);
				
				// 2.2) add the behaviour to the agent
				this.myAgent.addBehaviour(shareMapBehaviour);
				
				// 2.3) update the last communication time
				this.lastCommunicationTime.put(agentName, System.currentTimeMillis());
			}
			
			// 3) check if we need to communicate the SILO status with the agent
			Behaviour shareSiloBehaviour = checkForSiloCommunication(agentName);
			
			if (shareSiloBehaviour != null) {
				// 3.1) add the behaviour to the communication list
				Couple<String, Behaviour> couple = new Couple<String, Behaviour>(agentName, shareSiloBehaviour);
				this.currentCommunications.add(couple);

				// 3.2) add the behaviour to the agent
				this.myAgent.addBehaviour(shareSiloBehaviour);

				// 3.3) update the last communication time
				this.lastCommunicationTime.put(agentName, System.currentTimeMillis());
			}
			
		}
	}

	/*
	 * alternative version of checkForCommunication
	 * used to force a map communication with the SILO agent (still follows the timeBetweenMessagesAlt time limit between two messages)
	 */
	private void checkForCommunicationAlt(List<String> visibleAgents) {
		String siloName = "Silo";
		long now = System.currentTimeMillis();
		long lastCommunication = this.lastCommunicationTime.get(siloName);
		if ((now - lastCommunication) < this.timeBetweenMessagesAlt) {
			return;
		}
		// 1) force a map communication with the SILO agent
		HashMap<String, Object> content = new HashMap<String, Object>();	// additional content of the message (my position)
		content.put("position", getPosition().getLocationId());
		content.put("treasures",  new HashMap<>(this.knownTreasures));
		ShareMapBehaviour shareMapBehaviour = new ShareMapBehaviour(this.myAgent, siloName, this.myMap, this, content, mapVerbose);
		// 2) add the behaviour to the communication list
		Couple<String, Behaviour> couple = new Couple<String, Behaviour>(siloName, shareMapBehaviour);
		this.currentCommunications.add(couple);
		// 3) add the behaviour to the agent
		this.myAgent.addBehaviour(shareMapBehaviour);
		// 4) update the last communication time
		this.lastCommunicationTime.put(siloName, System.currentTimeMillis());
	}
	
	/*
	 * makes it easier to see what the agent is doing
	 * if time == 0 : Wait for an input from the user (enter in the console)
	 * if time > 0 : Wait for time milliseconds
	 */
	private void wait(int time) {
		if (time == 0) {
            try {
            	BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				reader.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        } else {
			this.myAgent.doWait(time);
		}
	}

	/*
	 * Update the map with the current position and the list of observations
	 * If an open node is directly reachable, returns its id
	 * TODO: it does not take into account the observations like chests, etc.
	 */
	private String updateMapWithObservations(Location myPosition, List<Couple<Location,List<Couple<Observation,String>>>> lobs) {
		// 1) remove the current node from openlist and add it to closedNodes.
		this.myMap.addNode(myPosition.getLocationId(), MapAttribute.closed);

		// 2) get the surrounding nodes and, if not in closedNodes, add them to open nodes.
		String nextNodeId = null;
		Iterator<Couple<Location, List<Couple<Observation, String>>>> iter = lobs.iterator();
		while(iter.hasNext()){
			Location accessibleNode = iter.next().getLeft();
			boolean isNewNode = this.myMap.addNewNode(accessibleNode.getLocationId());
			//the node may exist, but not necessarily the edge
			if (myPosition.getLocationId() != accessibleNode.getLocationId()) {
				this.myMap.addEdge(myPosition.getLocationId(), accessibleNode.getLocationId());
				if (nextNodeId == null && isNewNode) nextNodeId = accessibleNode.getLocationId();
			}
		}
		
		// 3) return the id of the next node to visit if it is directly reachable
		return nextNodeId;
    }
	
	/*
	 * returns True if the agent has finished exploring the map, False otherwise
	 * TODO: there might be more things to do, like to keep sharing the map with the other agents who are still exploring
	 */
	private boolean hasFinishedExploring() {
		return !this.myMap.hasOpenNode();
	}
	
	/*
	 * updates the path to destination
	 * - removes the first move from the list if it's the current position
	 * - if there are no moves planned or there are obstacles in the way, find the path to the closest accessible open node
	 * - pathToDestination will be null if there are no accessible open nodes
	 */
	public void updatepathToDestination(Location myPosition, List<String> obstacles) {
		// 1) check if there is already a list of moves to do
		if (this.pathToDestination != null && !this.pathToDestination.isEmpty()) {
			
			// 2) remove the first move from the list if it's the current position
			if (this.pathToDestination.get(0).equals(myPosition.getLocationId())) {
				this.pathToDestination.remove(0);
			}
			
			// 3) if there are still moves to do and it's not an obstacle : the next move is valid
			if (!this.pathToDestination.isEmpty() && !obstacles.contains(this.pathToDestination.get(0))) {
				return;
			}
		}
		
		// 4) if there are no moves to do with the current list or there are obstacles in the way, find the path to the closest accessible open node
		pathToDestination = this.myMap.getShortestPathToClosestOpenNodeWithObstacles(myPosition.getLocationId(), obstacles);
		// pathToDestination will be null if there are no accessible open nodes
	}

	/*
	 * returns the contents of an observable node
	 * returns null if the location is not observable
	 */
	public List<Couple<Observation, String>> getContentsOfObservableNode(String nodeId) {
        // 1) get the list of observations from the current position
		List<Couple<Location, List<Couple<Observation, String>>>> lobs = ((AbstractDedaleAgent) this.myAgent).observe();

		// 2) for each observable location :
		for (Couple<Location, List<Couple<Observation, String>>> l : lobs) {
			// if the location is the one we are looking for
			if (l.getLeft().getLocationId().equals(nodeId)) {
				if (verbose) {
					// System.out.println("Contents of observable node " + nodeId + " : " + l.getRight());
				}
				return l.getRight();
			}
		}

		// if the location is not observable, return null
		return null;
    }
	
	/*
	 * - updates the path to destination : 
	 * 		- recreates the list if the list was reset by another behaviour (for instance when new parts of the map are discovered)
	 * 	    - tests if an obstacle is in the way, if so, recreates the list with a new path
	 * - if we are on the destination, returns "onDestination"
	 * - if no path is found returns "noPathFound"
	 * - tries moving towards the destination
	 * - if it succeeds : returns "moved"
	 * - if it fails because of an agent in the way : it adds a behaviour to send a moveRequest message to the agent
	 *   returns "agentInWay"
	 * - if it fails for another reason : returns "failed"
	 */
	public String moveTowardsDestination(Location myPosition, List<String> obstacles, List<Couple<Location,List<Couple<Observation,String>>>> listObservations) {
		// 1) get the current destination
		String destination = (this.temporaryDestination != null) ? this.temporaryDestination : this.goalDestination;
		
		// 2) check if we are on the destination
		if (myPosition.getLocationId().equals(destination)) {
			return "onDestination";
		}
		
		// 3) update the path to destination
		// 3.1) recreate the list if the list was reset by another behaviour
		if (this.pathToDestination == null || this.pathToDestination.isEmpty()) {
			this.pathToDestination = this.myMap.getShortestPathToNodeWithObstacles(myPosition.getLocationId(), destination, obstacles);
		}
		
		// 3.2) if no path is found
		if (this.pathToDestination == null || this.pathToDestination.isEmpty()) {
			return "noPathFound";
		}
		
		// 3.3) test if there is an obstacle in the way
		for (String obstacle : obstacles) {
			if (this.pathToDestination.contains(obstacle)) {
				// 3.4) recreate the list with a new path
				this.pathToDestination = this.myMap.getShortestPathToNodeWithObstacles(myPosition.getLocationId(), destination, obstacles);
				break;
			}
		}
		
		// 4) if no path is found
		if (this.pathToDestination == null || this.pathToDestination.isEmpty()) {
			return "noPathFound";
		}
		
		// 5) try moving towards the destination
		boolean moved = moveToNextNode();
		
		// 6) if it succeeds
		if (moved) {
			this.isStationary = false;
			return "moved";
		}
		
		// 7) if it fails because an agent is on the next node
		// 7.1) get the next node
		String nextNode = this.pathToDestination.get(0);
		
		// 7.2) get the contents of the next node
		List<Couple<Observation, String>> contents = getContentsOfObservableNode(nextNode);
		
		// 7.3) test if there is an agent on the next node
		for (Couple<Observation, String> content : contents) {
			if (content.getLeft().toString().equals("AgentName")) {
				// test if it's golem
				String agentName = content.getRight();
				if (!list_agentNames.contains(agentName)) {
					// 7.4) add the golem to the list of golem sightings
					HashMap<String, Object> info = new HashMap<String, Object>();
					info.put("type", "Golem");
					info.put("timestamp", System.currentTimeMillis());
					this.golemSightings.put(nextNode, info);
					// 7.5) reset the path to destination
					this.pathToDestination = null;
					return "golemInWay";
				}
				
				// 7.4) check lastDeadlockMessage to avoid spamming the agent
				if (this.lastDeadlockMessage != null) {
					// same agent, same content and not enough time passed -> don't send the message
					boolean sameAgent = this.lastDeadlockMessage.get("recipient").equals(content.getRight());
					boolean sameContent = this.pathToDestination.equals(this.lastDeadlockMessage.get("content"));
					boolean notEnoughTimePassed = ((Long) this.lastDeadlockMessage.get("time") + this.timeDeadlockTreshold) > System.currentTimeMillis();
					
					if (sameAgent && sameContent && notEnoughTimePassed) {
						//System.out.println("\u001B[33m" + this.myAgent.getLocalName() + " - I already sent a deadlock message to " + content.getRight() + ", not sending it again" + "\u001B[0m");
						return "agentInWay";
					}
				}
				
				// 7.5) add a behaviour to send a moveRequest message to the agent
				int priority = this.temporaryDestination != null ? this.temporaryPriority : this.goalPriority;
				new SendMoveRequestBehaviour(this.myAgent, content.getRight(), myPosition.getLocationId(), this.pathToDestination, priority, moveRequestVerbose);
				
				// 7.6) update lastDeadlockMessage :
				this.lastDeadlockMessage = new HashMap<String, Object>();
				this.lastDeadlockMessage.put("recipient", content.getRight());
				this.lastDeadlockMessage.put("time", System.currentTimeMillis());
				this.lastDeadlockMessage.put("content", this.pathToDestination);
				
				// 7.7) return "agentInWay"
				return "agentInWay";
			}
		}
		
		// 8) if it fails for another reason
		return "failed";
	}
	
	/*
	 * moves to the next node in the list of moves to destination
	 * returns true if the agent moved, false otherwise
	 * also removes the first move from the list of moves if the agent moved
	 */
	public boolean moveToNextNode() {
		// 1) make sure there are moves to do
		if (this.pathToDestination == null || this.pathToDestination.isEmpty()) {
			return false;
		}
		
		// 2) move to the next node
		boolean moved = ((AbstractDedaleAgent) this.myAgent).moveTo(new GsLocation(this.pathToDestination.get(0)));
		
		// 3) remove the first move from the list if the agent moved
		if (moved) {
			this.pathToDestination.remove(0);
		}
		
		// 4) return if the agent moved
		return moved;
	}
	
	/*
	 * Merge the treasure info received from other agents
	 */
	public void mergeTreasureInfo(HashMap<String, TreasureInfo> receivedTreasures) {
        if (receivedTreasures == null || receivedTreasures.isEmpty()) return;

        for (HashMap.Entry<String, TreasureInfo> entry : receivedTreasures.entrySet()) {
            String locationId = entry.getKey();
            TreasureInfo receivedInfo = entry.getValue();

            TreasureInfo localInfo = this.knownTreasures.get(locationId);

            if (localInfo == null) { // new location: add it
                this.knownTreasures.put(locationId, receivedInfo);
                if (treasureVerbose) {
                	System.out.println("\u001B[32m" + this.myAgent.getLocalName() + " learned NEW treasure location via comms: " + locationId + " -> " + receivedInfo + ". Number of treasures: " + this.knownTreasures.size() + "\u001B[0m");
                }
            } else { // old location: check timestamp
                if (receivedInfo.timestamp > localInfo.timestamp) {
                    if (!localInfo.equals(receivedInfo)) {
	                     this.knownTreasures.put(locationId, receivedInfo);
	                     if (treasureVerbose) {
	                    	 System.out.println("\u001B[33m" + this.myAgent.getLocalName() + " UPDATED treasure info via comms at " + locationId + ". Old: " + localInfo + ", New: " + receivedInfo + "\u001B[0m");
	                     }
                    }
                }
            }
        }
	}
	

	/*
	 * Update the known treasures from the observations
	 */
	public void updateTreasuresFromObservation(List<Couple<Location, List<Couple<Observation, String>>>> listObservations) {
        String myPosId = getPosition().getLocationId();
        long currentTimestamp = System.currentTimeMillis();
        
        for (Couple<Location, List<Couple<Observation, String>>> obsEntry : listObservations) {
            String locId = obsEntry.getLeft().getLocationId();
            List<Couple<Observation, String>> details = obsEntry.getRight();

            // Process observations only at the agent's location
			if (!locId.equals(myPosId) || details.isEmpty()) {
				continue;
			}
            
            // 1. Store current raw observation details
            HashMap<String, String> currentObsMap = new HashMap<>();
            for (Couple<Observation, String> detail : details) {
                currentObsMap.put(detail.getLeft().toString(), detail.getRight());
            }

            // 2. Determine Treasure Type
            String observedType = null;
            if (currentObsMap.containsKey("Gold")) {
                observedType = "Gold";
            } else if (currentObsMap.containsKey("Diamond")) {
                observedType = "Diamond";
            }

            // 3. Get existing knowledge about this location (if any)
            TreasureInfo localInfo = this.knownTreasures.get(myPosId);

            // 4. Infer details (Type, Skills)
            String treasureType = observedType;
            int lockPicking = 0;
            int strength = 0;

            if (localInfo != null) {
            	// If we knew about it before, trust the previous type and skills
                if (treasureType == null) { // type missing in current obs: use the known type
                     treasureType = localInfo.type;
                     if (treasureVerbose) {
                    	 System.out.println("\u001B[37m" + myAgent.getLocalName() + " inferred type '" + treasureType + "' at " + myPosId + " from previous knowledge (amount likely 0)." + "\u001B[0m");
                     }
                }
                // Assert that observed type matches known type if both exist
                else if (!treasureType.equalsIgnoreCase(localInfo.type)) {
                	if (treasureInteractionsVerbose) {
                		System.err.println("\u001B[91mCRITICAL INCONSISTENCY: Observed type " + treasureType + " differs from known type " + localInfo.type + " at " + myPosId + ". Check environment/logic." + "\u001B[0m");
                	}
                     treasureType = localInfo.type;
                }
                // Trust known skills
                lockPicking = localInfo.requiredLockpicking;
                strength = localInfo.requiredStrength;
            } else { // new location: parse skills (default 0)
                try {
                	lockPicking = Integer.parseInt(currentObsMap.getOrDefault("LockPicking", "0"));
                    strength = Integer.parseInt(currentObsMap.getOrDefault("Strength", "0"));
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Could not parse skill values during first observation at " + myPosId);
                }
            }

            // 5. Parse dynamic details (Amount, Lock State) from current observation
            int observedAmount = 0;
            boolean observedLockOpen = false;
            try {
            	if (observedType != null && currentObsMap.containsKey(observedType)) {
            		observedAmount = Integer.parseInt(currentObsMap.get(observedType));
                }
                if (currentObsMap.containsKey("LockIsOpen")) {
                    observedLockOpen = Integer.parseInt(currentObsMap.get("LockIsOpen")) != 0;
                }
            } catch (NumberFormatException e) {
                System.err.println("Warning: Could not parse amount/lock values at " + myPosId);
            }


            // 6. Construct TreasureInfo
            if (treasureType != null) {
            	TreasureInfo observedState = new TreasureInfo(treasureType,observedAmount,observedLockOpen,lockPicking,strength,currentTimestamp);
            	if ((localInfo == null) || (!localInfo.equals(observedState))) {
            		this.knownTreasures.put(myPosId, observedState);
            		if (treasureVerbose) {
            			System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " observed state UPDATE at " + myPosId + ". Old: " + localInfo + ", New: " + observedState + ". Number of treasures: " + this.knownTreasures.size() + "\u001B[0m");
            		}
            	}
                // else: timestamp update or no change: do nothing

            } else {
            	if (treasureVerbose) {
            		System.out.println("\u001B[37m" + myAgent.getLocalName() + " observed details at " + myPosId + " but could not determine treasure type (not Gold/Diamond or first time seeing)." + "\u001B[0m");
            	}
            }
        }
	}
	
	
	/**
	 * Convert treasure type (String) to (Observation)
	 */
   private Observation treasureTypeToObservation(String type) {
       if (type == null) return null;
       switch (type.toLowerCase()) {
           case "gold": return Observation.GOLD;
           case "diamond": return Observation.DIAMOND;
           default: return null;
       }
   }
	
	/**
    * Attempts to open the lock on a treasure at the agent's current location.
    * Uses the combined expertise of nearby agents.
    */
   private boolean attemptOpenLock(String locationId) {
       Location myPosition = getPosition();
       if (myPosition == null || !myPosition.getLocationId().equals(locationId)) {
    	   if (treasureInteractionsVerbose) {
    		   System.err.println(myAgent.getLocalName() + " - Cannot attempt openLock, not at location " + locationId);
    	   }
           return false;
       }

       TreasureInfo treasureInfo = this.knownTreasures.get(locationId);
       if (treasureInfo == null) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println(myAgent.getLocalName() + " - No known treasure at " + locationId + " to attempt opening.");
       	   }
           return false;
       }

       if (treasureInfo.lockIsOpen) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println(myAgent.getLocalName() + " - Lock at " + locationId + " is already open.");
    	   }
           return true;
       }

       Observation treasureObsType = treasureTypeToObservation(treasureInfo.type);
       if (treasureObsType == null) {
    	   if (treasureInteractionsVerbose) {
    		   System.err.println(myAgent.getLocalName() + " - Unknown treasure type '" + treasureInfo.type + "' at " + locationId);
    	   }
           return false;
       }

       if (treasureInteractionsVerbose) {
    	   System.out.println(myAgent.getLocalName() + " - Attempting to open lock for " + treasureInfo.type + " at " + locationId);
       }
       boolean success = ((AbstractDedaleAgent) this.myAgent).openLock(treasureObsType);

       if (success) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println("\u001B[32m" + myAgent.getLocalName() + " - Successfully opened lock (or contributed) at " + locationId + "\u001B[0m");
    	   }
            TreasureInfo updatedInfo = treasureInfo.updateState(treasureInfo.amount, true, System.currentTimeMillis());
            this.knownTreasures.put(locationId, updatedInfo);
       } else {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println("\u001B[31m" + myAgent.getLocalName() + " - Failed to open lock at " + locationId + "\u001B[0m");
    	   }
       }
       return success;
   }

   /**
    * Attempts to pick up treasure from the agent's current location.
    * Checks for lock status, backpack space, and treasure type compatibility.
    */
   private int attemptPickUpTreasure(String locationId) {
       Location myPosition = getPosition();
       if (myPosition == null || !myPosition.getLocationId().equals(locationId)) {
    	   if (treasureInteractionsVerbose) {
    		   System.err.println(myAgent.getLocalName() + " - Cannot attempt pickUp, not at location " + locationId);
    	   }
           return 0;
       }

       TreasureInfo treasureInfo = this.knownTreasures.get(locationId);
       if (treasureInfo == null || treasureInfo.amount <= 0) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println(myAgent.getLocalName() + " - No known treasure or empty treasure at " + locationId);
    	   }
           return 0;
       }

       if (!treasureInfo.lockIsOpen) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println(myAgent.getLocalName() + " - Cannot pick up treasure at " + locationId + ", lock is closed.");
    	   }
           return 0;
       }

       // Check if agent can pick this type
       Observation agentTreasureType = ((AbstractDedaleAgent) this.myAgent).getMyTreasureType();
       Observation requiredTreasureType = treasureTypeToObservation(treasureInfo.type);

       if (agentTreasureType != requiredTreasureType) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println(myAgent.getLocalName() + " - Cannot pick up " + treasureInfo.type + ", agent type is " + agentTreasureType);
    	   }
            return 0;
       }


       // Check backpack space
       List<Couple<Observation, Integer>> freeSpaceList = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();
       int availableSpace = 0;
       for(Couple<Observation, Integer> space : freeSpaceList) {
           if (space.getLeft() == requiredTreasureType) {
               availableSpace = space.getRight();
               break;
           }
       }

       if (availableSpace <= 0) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println(myAgent.getLocalName() + " - No backpack space for " + treasureInfo.type + " at " + locationId);
    	   }
           return 0;
       }

       int amountPicked = ((AbstractDedaleAgent) this.myAgent).pick();

       if (amountPicked > 0) {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println("\u001B[32m" + myAgent.getLocalName() + " - Successfully picked up " + amountPicked + " " + treasureInfo.type + " at " + locationId + "\u001B[0m");
    	   }
           // Update local knowledge
           int newAmount = treasureInfo.amount - amountPicked;
           TreasureInfo updatedInfo = treasureInfo.updateState(newAmount, treasureInfo.lockIsOpen, System.currentTimeMillis());
           this.knownTreasures.put(locationId, updatedInfo);
           if (treasureInteractionsVerbose) {
        	   System.out.println("\u001B[36m" + myAgent.getLocalName() + " - Updated known treasure at " + locationId + " to: " + updatedInfo + "\u001B[0m");
           }
       } else {
    	   if (treasureInteractionsVerbose) {
    		   System.out.println("\u001B[31m" + myAgent.getLocalName() + " - Failed to pick up treasure at " + locationId + " (API returned 0)\u001B[0m");
    	   }
           // Might happen if amount changed between observation and pick attempt
           // Re-observing is the best way to sync.
       }

       return amountPicked;
   }

   /**
    * Attempts to deposit the agent's backpack contents into a specified Silo agent.
    *
    */
   private boolean attemptDepositTreasure(String siloName) {
	  HashMap<String, Object> observations = getObservations();
	  List<String> visibleAgents = (List<String>) observations.get("visibleAgents");
       
      // Check if Silo is visible/nearby
      if (visibleAgents == null || !visibleAgents.contains(siloName)) {
    	  if (treasureInteractionsVerbose) {
    		  System.out.println(myAgent.getLocalName() + " - Silo '" + siloName + "' not visible at current location, cannot deposit.");
    	  }
           return false;
      }

      List<Couple<Observation, Integer>> freeSpaceBefore = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();

      if (treasureInteractionsVerbose) {
    	  System.out.println(myAgent.getLocalName() + " - Attempting to deposit treasure into Silo '" + siloName + "'");
      }
      boolean emptySuccess = ((AbstractDedaleAgent) this.myAgent).emptyMyBackPack(siloName);

      if (!emptySuccess) {
    	  if (treasureInteractionsVerbose) { 
    		  System.out.println("\u001B[31m" + myAgent.getLocalName() + " - API call emptyMyBackPack failed for Silo '" + siloName + "' (returned false).\u001B[0m");
    	  }
           return false;
      }

      List<Couple<Observation, Integer>> freeSpaceAfter = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();

      boolean spaceChanged = !freeSpaceBefore.equals(freeSpaceAfter);
      if (spaceChanged) {
    	  if (treasureInteractionsVerbose) {
    		  System.out.println("\u001B[32m" + myAgent.getLocalName() + " - Successfully deposited treasure into Silo '" + siloName + "' (backpack space changed : " + freeSpaceBefore + " -> " + freeSpaceAfter + ").\u001B[0m");
    	  }
          return true;
      } else {
    	  if (treasureInteractionsVerbose) {
    		  System.out.println(myAgent.getLocalName() + " - Deposit attempt made to Silo '" + siloName + "', but backpack space didn't change.");
    	  }
          return false;
      }
  }
	
	
	/*
	 * SILO
	 * finds the optimal position for the silo
	 * Principle : We assign each node a score and choose the node with the LOWEST score
	 * the score is calculated with : (the distance to all nodes in the graph) * (multiplier based on not creating deadlocks (interblockage))
	 */
	private String findOptimalSiloPosition() {
		// 1) initialize the variables
		String bestNodeFound = null;
		Float bestScoreFound = Float.MAX_VALUE;
		
		// 2) get the list of nodes
		List<String> nodes = this.myMap.getNodes();
		
		// 3) for each node, calculate the score
		for (String node : nodes) {
			// 3.0) get the neighbor nodes
			List<String> neighborNodes = new ArrayList<String>();
			for (Node neighborNode : this.myMap.getNeighborNodes(node)) {
				neighborNodes.add(neighborNode.getId());
			}
			
			// 3.1) initialize the multiplier to 1 (no penalty)
			float multiplier = 1;
			
			// 3.2) get the distance to all nodes
			//int distanceToAllNodes = this.myMap.getDistanceToAllNodes(node);

			// 3.2) get the distance to all nodes with treasures
			List<String> treasures = new ArrayList<String>();
			for (String treasureId : this.knownTreasures.keySet()) {
				treasures.add(treasureId);
			}
			int distanceToAllNodes = this.myMap.getDistanceToAllNodes(node, treasures);
			
			// 3.3) reduce the deadlocks
			// 3.3.1) get a node with a high number of edges			
			int numberOfEdges = neighborNodes.size();
			multiplier *= (float) 1 / (float) (numberOfEdges*numberOfEdges*numberOfEdges);
			
			// 3.3.2) avoid blocking paths
			// IDEA : test what the best path is between each adjacent node if the silo occupies (and therefore blocks) 
			// the current node. If the distance is highly increased that means we are blocking a *probably* useful path
			if (numberOfEdges > 1) {
				float distanceBetweenNeighbours = 0;
				neighboursCouplesLoop:
				for (int neighbor1Index = 0; neighbor1Index < neighborNodes.size()-1; neighbor1Index++) {
					for (int neighbor2Index = neighbor1Index + 1; neighbor2Index < neighborNodes.size(); neighbor2Index++) {
						String neighbor1 = neighborNodes.get(neighbor1Index);
						String neighbor2 = neighborNodes.get(neighbor2Index);
						List<String> obstacleNodes = new ArrayList<String>();
						obstacleNodes.add(node);
						List<String> distance = this.myMap.getShortestPathToNodeWithObstacles(neighbor1, neighbor2, obstacleNodes);
						if (distance == null) {
							// The graph becomes discontinuous if the silo is blocking the current node
							distanceBetweenNeighbours = -1;
							// break the loop
							break neighboursCouplesLoop;
						}
						else {
							distanceBetweenNeighbours += distance.size();
						}
					}
				}
				distanceBetweenNeighbours /= neighborNodes.size();	//use the mean to not penalise nodes with a higher degree
				if (distanceBetweenNeighbours <= 0) {
					// Very high penalty for making the graph discontinuous
					multiplier *= 100;
				}
				else {
					// reduce the multiplier based on the distance between neighbours when the silo is blocking the current node
					multiplier *= (float) distanceBetweenNeighbours / 10;
				}
			}
			
			// 3.4) calculate the score
			float score = (float) distanceToAllNodes * multiplier;
			if (optimalSiloNodeVerbose) {
				System.out.println("Node " + node + " has a score of " + score);
			}
			if (score < bestScoreFound) {
				bestScoreFound = score;
				bestNodeFound = node;
			}
		}

		// 4) return the best node found
		if (optimalSiloNodeVerbose) {
			System.out.println("Best node found is " + bestNodeFound + " with a score of " + bestScoreFound);
			System.out.println("current silo position is " + this.correctSiloPosition);
			System.out.println("current silo status is " + this.siloPositionStatus);
		}
		return bestNodeFound;
	}
	
	/*
	 * SILO
	 * fill agentsToNotifyOfNewSiloPosition with all the agents
	 */
	private void fillAgentsToNotifyOfNewSiloPosition() {
		this.agentsToNotifyOfNewSiloPosition.clear();
		for (String agentName : this.list_agentNames) {
			if (!agentName.equals(this.myAgent.getLocalName())) {
				this.agentsToNotifyOfNewSiloPosition.add(agentName);
			}
		}
	}
	
	/*
	 * SILO
	 * removes the agent from the agentsToNotifyOfNewSiloPosition list
	 */
	public void removeAgentFromAgentsToNotifyOfNewSiloPosition(String agentName) {
		// 0) if the agent is not in the list, return
		if (!this.agentsToNotifyOfNewSiloPosition.contains(agentName)) {
			return;
		}
		
		// 1) remove the agent from the list of agents to notify
		this.agentsToNotifyOfNewSiloPosition.remove(agentName);

		// COMMENTED because it is already handled in actionExploitationSilo, so it should no longer be needed
//		// 2) check if there are still agents to notify
//		if (this.agentsToNotifyOfNewSiloPosition.isEmpty()) {
//			// 3) there are no more agents to notify, we can start moving
//			this.siloPositionStatus = 1;
//			this.goalDestination = this.correctSiloPosition;
//			this.goalPriority = 800;
//			this.pathToDestination = null;
//			this.temporaryDestination = null;
//			this.setSiloKnowledge(System.currentTimeMillis(), 1, this.correctSiloPosition, "-1");
//		}
	}
	
	/*
	 * SILO
	 * update the correctSiloPosition if needed
	 */
	private void updateCorrectSiloPosition() {
		// 1) check if the position needs an update
		
		// 1.1) check if the final position has been found already (map is already completely explored)
		if (this.finalSiloPositionFoundFlag) {
			return;
		}
		
		// 1.2) check if we are already moving or about to change position
		if (this.siloPositionStatus == 1 || this.siloPositionStatus == 3) {
			return;
		}
		
		// 1.3) check if enough time has passed since the last update
		if (System.currentTimeMillis() - this.lastSiloPositionUpdateTime < timeBetweenSiloPositionUpdates) {
			return;
		}
		
		// 1.4) update the lastSiloPositionUpdateTime
		this.lastSiloPositionUpdateTime = System.currentTimeMillis();
		
		// 2) find the new best position
		String newBestPosition = findOptimalSiloPosition();
		
		// 3) if all nodes are closed, set the final position flag
		if (this.myMap.getOpenNodes().isEmpty()) {
			this.finalSiloPositionFoundFlag = true;
			if (optimalSiloNodeVerbose) {
				System.out.println("\u001B[36m" + this.myAgent.getLocalName()
						+ " - All nodes are closed, setting final position flag to true" + "\u001B[0m");
			}
		}
		
		// 4) test if the new best position is different from the current one
		if (this.correctSiloPosition == null || !this.correctSiloPosition.equals(newBestPosition)) {
			if (optimalSiloNodeVerbose) {
				System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - New best Silo position found : "+ newBestPosition + "\u001B[0m");
			}
			// 5) update the status
			if (this.siloPositionStatus == 0) {
				// 5.1) if this is the first time we set the position -> move immediately
				this.siloPositionStatus = 1; // in transit to new position
				this.goalDestination = newBestPosition;
				this.goalPriority = 800;
				this.pathToDestination = null;
				this.temporaryDestination = null;
				this.setSiloKnowledge(System.currentTimeMillis(), 1, newBestPosition, "-1");
			}
			else {
				this.siloPositionStatus = 3; // about to change position
				this.phase3Time = System.currentTimeMillis();
				this.setSiloKnowledge(System.currentTimeMillis(), 3, this.correctSiloPosition, newBestPosition);
				
				// 6) fill the agentsToNotifyOfNewSiloPosition list to make sure all agents are aware of the new position
				fillAgentsToNotifyOfNewSiloPosition();
				
			}

			// 7) update the attributes
			this.correctSiloPosition = newBestPosition;
			// this.goalDestination = newBestPosition;	// not yet, we need to wait for the agents to be aware of the new position
			
		}
		
	}
	
	/*
	 * get neighbour nodes
	 */
	private List<String> getNeighborNodes(String nodeId) {
		List<String> neighborNodes = new ArrayList<String>();
		for (Node neighborNode : this.myMap.getNeighborNodes(nodeId)) {
			neighborNodes.add(neighborNode.getId());
		}
		return neighborNodes;
	}
	
	/**
	 * Action for phase 1
	 */
	private void actionExploration(Location myPosition, List<Couple<Location,List<Couple<Observation,String>>>> listObservations, List<String> visibleAgents, List<String> obstacles) {
		
		// 3) update the map
		updateMapWithObservations(myPosition, listObservations);
		
		// 4) check if there is a visible agent we want to communicate with
		checkForCommunication(visibleAgents);
		
		// 5) check if we are currently communicating with an agent
		if (!this.currentCommunications.isEmpty()) {
			return;
		}
		
		// 6) update the path to destination
		updatepathToDestination(myPosition, obstacles);
		
		// 7) test if we need to change the phase
		boolean noMovesLeft = (pathToDestination == null) || (pathToDestination.isEmpty());
		if (hasFinishedExploring() || noMovesLeft) {
			if (isSiloAgent) {
				if (verbose) {
					System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - I know all nodes or can't find any accessible open nodes, changing to phase 3" + "\u001B[0m");
				}
				phase = 3;
			}
			else {
				if (verbose) {
					System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - I know all nodes or can't find any accessible open nodes, changing to phase 2" + "\u001B[0m");
				}
				phase = 2;
			}
			return;
		}
		
		// 8) move
		boolean moved = moveToNextNode();

	}
	
	
	
	
	
	
	private void communicateWithSiloAgent() {
		// 1) attempt to deposit any treasure in the silo
		attemptDepositTreasure("Silo");
		
		// 2) general communications
		List<String> agentsToCommunicateWith = new ArrayList<String>();
		agentsToCommunicateWith.add("Silo");
		checkForCommunicationAlt(agentsToCommunicateWith);
		
		// 3) content to share
		HashMap<String, Object> content = new HashMap<String, Object>();
		Set<dataStructures.tuple.Couple<Observation, Integer>> expertise = ((AbstractDedaleAgent) this.myAgent).getMyExpertise();
		int strength = expertise.stream().filter(e -> e.getLeft() == Observation.STRENGH).mapToInt(Couple::getRight).sum();
		int lockPicking = expertise.stream().filter(e -> e.getLeft() == Observation.LOCKPICKING).mapToInt(Couple::getRight).sum();
		List<Couple<Observation,Integer>> capacity = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();
		int diamondCapacity = capacity.stream().filter(e -> e.getLeft() == Observation.DIAMOND).mapToInt(Couple::getRight).sum();
		int goldCapacity = capacity.stream().filter(e -> e.getLeft() == Observation.GOLD).mapToInt(Couple::getRight).sum();
		
		content.put("strengthExpertise", strength);
		content.put("lockPickingExpertise", lockPicking);
		content.put("ressourceType", ((AbstractDedaleAgent) this.myAgent).getMyTreasureType());
		content.put("backPackCapacityGold", goldCapacity);
		content.put("backPackCapacityDiamond", diamondCapacity);
		content.put("purpose", "newMission");
		content.put("name", this.myAgent.getLocalName());

		CommunicateWithSiloBehaviour behaviour = new CommunicateWithSiloBehaviour(this.myAgent, "Silo", this, content, communicateWithSiloVerbose);
		
		// 4) add the behaviour to the communication list
		Couple<String, Behaviour> couple = new Couple<String, Behaviour>("Silo", behaviour);
		this.currentCommunications.add(couple);
		
		// 4.2) add the behaviour to the agent
		this.myAgent.addBehaviour(behaviour);
		
		// 4.3) update the last communication time
		//this.lastCommunicationTime.put("Silo", System.currentTimeMillis());
	}
	
	public void newMission(HashMap<String, Object> content) {
		if (missionVerbose) {
			System.out.println(this.myAgent.getLocalName() + " - New mission received : " + content);
		}
		
		if (content == null) {	// est déjà arrivé par le passé je ne sais pas pourquoi
			if (missionVerbose) {
				System.out.println(this.myAgent.getLocalName() + " - New mission content is null");
			}
			return;
		}

		// 1) get the content
		String missionType = (String) content.get("missionType");
		List<String> missionDestinations = (List<String>) content.get("missionDestinations");
		long timeOut = (long) content.get("timeOut");
		
		switch (missionType) {
		case "explore":
			this.setUpExploreMission(timeOut);
			break;
		case "waitForSiloMessage":
			this.setUpWaitForSiloMessage(timeOut);
			break;
		case "exploreOpenNodes":
			String openNode = (String) missionDestinations.get(0);
			this.setUpExploreOpenNodesMission(timeOut, openNode);
			break;
		case "unlockTreasure":
			this.setUpUnlockTreasureMission(missionDestinations, timeOut);
			break;
		case "pickUpTreasure":
			this.setUpPickUpTreasureMission(missionDestinations, timeOut);
			break;
		case "unlockAndPickUpTreasure":
			this.setUpUnlockAndPickUpTreasureMission(missionDestinations, timeOut);
			break;
		default:
			if (missionVerbose) {
				System.out.println(this.myAgent.getLocalName() + " - Unknown mission type : " + missionType);
			}
			return;
		}


	}
	
	
	
	
	
	
	private void setUpGoToSiloMission() {
		this.missionType = "goToSilo";
		// get new destinations and set the goal destination
		String siloPosition = this.getSiloPosition();
		if (siloPosition == null) {
			if (missionVerbose) {
				System.out.println(
						this.myAgent.getLocalName() + " - Silo position is null, can't set up goToSilo mission");
			}
			return;
		}
		this.missionDestinations = getNeighborNodes(siloPosition);
		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - Neighbor nodes of silo position " + siloPosition + " are : " + this.missionDestinations + "\u001B[0m");
		}

		HashMap<String, Object> results = this.myMap.getClosestNodeFromList(getPosition().getLocationId(), this.missionDestinations, getObstacles());
		
		String closestNode = (String) results.get("closestNode");
		List<String> path = (List<String>) results.get("path");
		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - Closest node from list is " + closestNode + "\u001B[0m");
		}
		
		this.goalDestination = closestNode;
		this.pathToDestination = path;
		this.goalPriority = 400;
		this.isStationary = false;
		
		this.missionTimeout = System.currentTimeMillis() + 15000;
	}
	
	private void setUpFindSiloMission() {
		this.missionType = "findSilo";
		this.goalDestination = this.myMap.getRandomNode().getId();
		this.pathToDestination = null;
		this.goalPriority = (int) (1 + Math.random() * 100);
		this.isStationary = false;
		
		this.missionTimeout = System.currentTimeMillis() + 10000;
		
		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - Setting up findSilo mission, goal destination is " + this.goalDestination + "\u001B[0m");
		}
	}
	
	private void setUpExploreMission(long missionTimeout) {
		this.missionType = "explore";
		this.goalDestination = this.myMap.getRandomNode().getId();
		this.pathToDestination = null;
		this.goalPriority = 0;
		this.isStationary = false;

		this.missionTimeout = missionTimeout;

		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - Setting up explore mission, goal destination is " + this.goalDestination + "\u001B[0m");
		}
	}
	
	private void setUpWaitForSiloMessage(long missionTimeout) {
		this.missionType = "waitForSiloMessage";

		String siloPosition = this.getSiloPosition();
		this.missionDestinations = getNeighborNodes(siloPosition);
		
		HashMap<String, Object> results = this.myMap.getClosestNodeFromList(getPosition().getLocationId(), this.missionDestinations, getObstacles());
		
		String closestNode = (String) results.get("closestNode");
		List<String> path = (List<String>) results.get("path");
		
		this.goalDestination = closestNode;
		this.pathToDestination = path;
		this.goalPriority = 100;
		
		String myPosition = getPosition().getLocationId();
		this.isStationary = closestNode.equals(myPosition);
		
		this.missionTimeout = missionTimeout;

		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - Setting up waitForSiloMessage mission"
					+ "\u001B[0m");
		}
	}
	
	private void setUpExploreOpenNodesMission(long missionTimeout, String openNode) {
		this.missionType = "exploreOpenNodes";
		this.goalDestination = openNode;
		this.pathToDestination = null;
		this.goalPriority = 300;

		this.missionTimeout = missionTimeout;

		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName()
					+ " - Setting up exploreOpenNodes mission, goal destination is " + this.goalDestination
					+ "\u001B[0m");
		}
	}
	
	private void setUpUnlockTreasureMission(List<String> missionDestinations, long missionTimeout) {
		this.missionType = "unlockTreasure";
		this.missionDestinations = missionDestinations;
		this.goalDestination = this.missionDestinations.get((int) (Math.random() * this.missionDestinations.size()));
		this.pathToDestination = null;
		this.goalPriority = 650;

		this.missionTimeout = missionTimeout;

		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName()
					+ " - Setting up unlockTreasure mission, goal destination is " + this.goalDestination
					+ "\u001B[0m");
		}
	}
	
	private void setUpPickUpTreasureMission(List<String> missionDestinations, long missionTimeout) {
		this.missionType = "pickUpTreasure";
		this.missionDestinations = missionDestinations;
		this.goalDestination = this.missionDestinations.get((int) (Math.random() * this.missionDestinations.size()));
		this.pathToDestination = null;
		this.goalPriority = 625;
		this.isStationary = false;

		this.missionTimeout = missionTimeout;

		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName()
					+ " - Setting up pickUpTreasure mission, goal destination is " + this.goalDestination
					+ "\u001B[0m");
		}
	}
	
	private void setUpUnlockAndPickUpTreasureMission(List<String> missionDestinations, long missionTimeout) {
		this.missionType = "unlockAndPickUpTreasure";
		this.missionDestinations = missionDestinations;
		this.goalDestination = this.missionDestinations.get((int) (Math.random() * this.missionDestinations.size()));
		this.pathToDestination = null;
		this.goalPriority = 675;
		this.isStationary = false;

		this.missionTimeout = missionTimeout;

		if (missionVerbose) {
			System.out.println("\u001B[36m" + this.myAgent.getLocalName()
					+ " - Setting up unlockAndPickUpTreasure mission, goal destination is " + this.goalDestination
					+ "\u001B[0m");
		}
	}
	
	/*
	 * handles the missions
	 */
	private void handleCurrentMission() {
		if (this.missionType == null) {
			if (missionVerbose) {
				System.out.println("\u001B[36m" + "Mission type is null" + "\u001B[0m");
			}
			// 6.1) use "goToSilo" as default mission type
			if (this.considerSiloLost) {
				// backup plan to avoid deadlock from everyone having outdated silo position and for some reason not switching to "findSilo" (bug)
				if (missionVerbose) {
					System.out.println("\u001B[36m" + "Silo is lost, setting mission to fildSilo" + "\u001B[0m");
				}
				setUpFindSiloMission();
				return;
			}
			String siloPosition = this.getSiloPosition();
			if (siloPosition == null || siloPosition.equals("-1")) {
				// 6.2) if we don't have a silo position, set the mission to "findSilo" instead
				if (missionVerbose) {
					System.out.println("\u001B[36m" + "Silo position is null, setting mission to findSilo" + "\u001B[0m");
				}
				setUpFindSiloMission();
				return;
			}
			else {
				if (missionVerbose) {
					System.out.println("\u001B[36m"+ "Silo position is not null, setting mission to goToSilo" + "\u001B[0m");
				}
				setUpGoToSiloMission();
				return;
			}
		}
		
		// timeout
		if (System.currentTimeMillis() > this.missionTimeout) {
			if (missionVerbose) {
				System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - Mission : " + this.missionType + " timed out, setting it to null" + "\u001B[0m");
			}
			this.missionType = null;
			return;
		}
		
		if (this.missionType.equals("goToSilo")) {
			HashMap<String, Object> results = this.myMap.getClosestNodeFromList(getPosition().getLocationId(), this.missionDestinations, getObstacles());
			
			String closestNode = (String) results.get("closestNode");
			if (closestNode == null) {
				return;
			}
			List<String> path = (List<String>) results.get("path");
			
			this.goalDestination = closestNode;
			this.pathToDestination = null;
			//this.pathToDestination = path;
		}
		
		if (this.missionType.equals("waitForSiloMessage")) {
			HashMap<String, Object> results = this.myMap.getClosestNodeFromList(getPosition().getLocationId(), this.missionDestinations, getObstacles());

			String closestNode = (String) results.get("closestNode");
			if (closestNode == null) {
				return;
			}
			List<String> path = (List<String>) results.get("path");
			
			this.goalDestination = closestNode;
			this.pathToDestination = null;
			//this.pathToDestination = path;
			
			String myPosition = getPosition().getLocationId();
			if (closestNode.equals(myPosition)) {
				this.isStationary = true;
			}
		}
		
		if (this.missionType.equals("unlockTreasure")) {
			HashMap<String, Object> results = this.myMap.getClosestNodeFromList(getPosition().getLocationId(), this.missionDestinations, getObstacles());
            
            String closestNode = (String) results.get("closestNode");
			if (closestNode == null) {
				return;
			}
            List<String> path = (List<String>) results.get("path");
            
            this.goalDestination = closestNode;
			this.pathToDestination = null;
			//this.pathToDestination = path;
            
            String myPosition = getPosition().getLocationId();
			if (closestNode.equals(myPosition)) {
				this.isStationary = true;
				// attempt to unlock the treasure
				boolean isLockOpen = attemptOpenLock(closestNode);
		        if (isLockOpen) {
	                this.missionType = null; // Mark mission as done
	                return;
		        }
			}
		}
		
		if (this.missionType.equals("pickUpTreasure")) {
			HashMap<String, Object> results = this.myMap.getClosestNodeFromList(getPosition().getLocationId(), this.missionDestinations, getObstacles());

			String closestNode = (String) results.get("closestNode");
			if (closestNode == null) {
				return;
			}
			List<String> path = (List<String>) results.get("path");

			this.goalDestination = closestNode;
			this.pathToDestination = null;
			//this.pathToDestination = path;

			String myPosition = getPosition().getLocationId();
			if (closestNode.equals(myPosition)) {
				this.isStationary = true;
				// attempt to pick up the treasure
				int pickedAmount = attemptPickUpTreasure(myPosition);
                TreasureInfo treasureInfo = this.knownTreasures.get(myPosition);
                if (pickedAmount > 0 || (treasureInfo != null && treasureInfo.amount == 0 && treasureInfo.lockIsOpen)) {
                     if (missionVerbose) {
                        System.out.println("\u001B[32m" + this.myAgent.getLocalName() + " - pickUpTreasure at " + myPosition + " complete or treasure gone. Picked: " + pickedAmount + "\u001B[0m");
                    }
                    this.missionType = null;
                    return;
                } else if (treasureInfo != null && !treasureInfo.lockIsOpen) {
                     if (missionVerbose) {
                        System.out.println("\u001B[31m" + this.myAgent.getLocalName() + " - pickUpTreasure at " + myPosition + ": Lock is closed. Mission should have been unlockAndPickUpTreasure or unlockTreasure first." + "\u001B[0m");
                        this.missionType = null;
                        return;
                        //TODO this can be another treasure maybe ?
                    }
                } else {
                    if (missionVerbose) {
                        System.out.println("\u001B[33m" + this.myAgent.getLocalName() + " - pickUpTreasure at " + myPosition + ": Failed to pick (e.g. no capacity, wrong type, or already empty but knownTreasures not updated). Mission continues." + "\u001B[0m");
                    }
                }
			}
		}
		
		if (this.missionType.equals("unlockAndPickUpTreasure")) {
			HashMap<String, Object> results = this.myMap.getClosestNodeFromList(getPosition().getLocationId(), this.missionDestinations, getObstacles());

			String closestNode = (String) results.get("closestNode");
			if (closestNode == null) {
				return;
			}
			List<String> path = (List<String>) results.get("path");

			this.goalDestination = closestNode;
			this.pathToDestination = null;
			//this.pathToDestination = path;

			String myPosition = getPosition().getLocationId();
			if (closestNode.equals(myPosition)) {
				this.isStationary = true;
				// attempt to unlock and pick up the treasure
				boolean isLockOpen = attemptOpenLock(myPosition);
				int pickedAmount = attemptPickUpTreasure(myPosition);
				TreasureInfo treasureInfo = this.knownTreasures.get(myPosition);
				if (pickedAmount > 0 || (treasureInfo != null && treasureInfo.amount == 0 && treasureInfo.lockIsOpen)) {
					if (missionVerbose) {
						System.out.println("\u001B[32m" + this.myAgent.getLocalName() + " - unlockAndPickUpTreasure at "
								+ myPosition + " complete or treasure gone. Picked: " + pickedAmount + "\u001B[0m");
					}
					this.missionType = null;
					return;
				} else if (treasureInfo != null && !treasureInfo.lockIsOpen) {
					if (missionVerbose) {
						System.out.println("\u001B[31m" + this.myAgent.getLocalName() + " - unlockAndPickUpTreasure at "
								+ myPosition
								+ ": Lock is closed. Mission should have been unlockAndPickUpTreasure or unlockTreasure first."
								+ "\u001B[0m");
						this.missionType = null;
						return;
						// TODO this can be another treasure maybe ?
					}
				} else {
					if (missionVerbose) {
						System.out.println("\u001B[33m" + this.myAgent.getLocalName() + " - unlockAndPickUpTreasure at "
								+ myPosition
								+ ": Failed to pick (e.g. no capacity, wrong type, or already empty but knownTreasures not updated). Mission continues."
								+ "\u001B[0m");
					}
				}
			}
		}
	}

	
	private void actionExploitationAgent(Location myPosition, List<Couple<Location,List<Couple<Observation,String>>>> listObservations, List<String> visibleAgents, List<String> obstacles) {
		// 2) pickup treasure if mission is to pick up treasure
		// this is a temorary solution until I can find out why it won't work when used at the end of the execution
		if (this.missionType != null) {
			if (this.missionType.equals("pickUpTreasure") || this.missionType.equals("unlockAndPickUpTreasure")) {
				String myPositionId = myPosition.getLocationId();
				if (missionVerbose) {
					System.out.println("pickUpTreasure");
					System.out.println("TTTTTTTTTTTTTTT : " + myPositionId);
				}
				attemptPickUpTreasure(myPositionId);
			}
		}
		
		// 3) update the map with the observations
		updateMapWithObservations(myPosition, listObservations);
		
		// 4) check if there is a visible agent we want to communicate with
		checkForCommunication(visibleAgents);
		
		// 5) check if we are currently communicating with an agent
		if (!this.currentCommunications.isEmpty()) {
			return;
		}
		
		// 5) check how long it's been since the last interaction with the silo agent
		if (System.currentTimeMillis() - this.lastSiloInteractionTimeStamp > considerSiloLostAfter) {
			// consider silo lost
			this.considerSiloLost = true;
			this.siloPositionStatus = 0;
			this.correctSiloPosition = null;
		}
		
		// 6) get obstacles
		obstacles = getObstacles();
		
		// 6) mission :
		handleCurrentMission();
		
		// 6) try to move towards the destination (or temporary destination if there is one)
		String moved = moveTowardsDestination(myPosition, obstacles, listObservations);
		
		// 7) handle the different cases
		//handleMoveResult(moved);
		switch (moved) {
		case "onDestination":
			// 7.1) if we are on the destination, remove the temporary destination
			this.temporaryDestination = null;

			// 7.3) test if we are on the goal destination
			if (this.goalDestination != null && this.goalDestination.equals(myPosition.getLocationId())) {
				// 7.4) TODO: Do whatever we came here to do, for now just print a message
				if (this.missionType != null) {
				
					switch (this.missionType) {
					case "goToSilo":
						// 7.4.1) if we are on the silo position, communicate with the silo agent
						if (missionVerbose) {
							System.out.println("\u001B[1m" + this.myAgent.getLocalName() + " - I am on the silo position, sending a message to the silo agent" + "\u001B[0m");
						}
						communicateWithSiloAgent();
						//setUpFindSiloMission();	//TODO: just testing
						break;
					case "findSilo":
						// 7.4.2) if we are on the goal destination, try to got to the silo if found, or find the silo otherwise
						this.missionType = null;		// will set up either a goToSilo or findSilo mission on next iteration
						// handleCurrentMission();
						break;
					case "explore":
						// 7.4.3) if we are on the goal destination, pick a new random destination
						this.goalDestination = this.myMap.getRandomNode().getId();
						break;
					case "exploreOpenNodes":
						// 7.4.4) if we are on the goal destination, pick a new open node
						List<String> openNodes = this.myMap.getOpenNodes();
						if (openNodes.isEmpty()) {
							// no more open nodes
							this.missionType = null;
						}
						else {
							String openNode = openNodes.get((int) (Math.random() * openNodes.size()));
							this.goalDestination = openNode;
							this.pathToDestination = null;
						}
						break;
					case "unlockTreasure":
						if (missionVerbose) {
							System.out.println("unlockTreasure");
						}
						// 7.4.5) if we are on the goal destination, try to unlock the treasure
						attemptOpenLock(myPosition.getLocationId());
						break;
					case "unlockAndPickUpTreasure":
						if (missionVerbose) {
							System.out.println("unlockAndPickUpTreasure");
						}
						// 7.4.5) if we are on the goal destination, try to unlock and pick up the
						// treasure
						attemptOpenLock(myPosition.getLocationId());
						attemptPickUpTreasure(myPosition.getLocationId());
						break;
					case "pickUpTreasure":
						if (missionVerbose) {
							System.out.println("pickUpTreasure");
						}
						// 7.4.6) if we are on the goal destination, try to pick up the treasure
						attemptPickUpTreasure(myPosition.getLocationId());
						break;
					}
				}
				//this.isStationary = true;
				if (verbose) {
					System.out.println("\u001B[1m" + this.myAgent.getLocalName() + " - I reached the destination" + "\u001B[0m");
				}
			}
			break;
		case "noPathFound":
			// 7.1) if no path is found, become stationary (to have a lower priority)
			this.isStationary = true;
			if (verbose) {
				System.out.println("\u001B[1m" + this.myAgent.getLocalName() + " - I can't find a path to the destination" + "\u001B[0m");
			}
			break;
		case "moved":
			// 7.1) if the agent moved, update the number of updates since last communication (not anymore since we use the timestamp since last communication)
			//updateUpdatesSinceLastCommunication();
			break;
		case "agentInWay":
			// 7.1) if an agent is in the way, do nothing, moveTowardsDestination() has already added a behaviour to send a message to the agent
			if (verbose) {
				//System.out.println("\u001B[1m" + this.myAgent.getLocalName() + " - An agent is in the way" + "\u001B[0m");
			}
			break;
		case "failed":
			// 7.1) if it failed for another reason, do nothing, for now we just print a message
			if (verbose) {
				System.out.println("\u001B[1m" + this.myAgent.getLocalName() + " - I failed to move to the destination" + "\u001B[0m");
			}
			break;
		case "golemInWay":
			// 7.1) if a golem is in the way, do nothing, the next iteration will recreate the path to destination
			if (verbose) {
				System.out
						.println("\u001B[1m" + this.myAgent.getLocalName() + " - A golem is in the way" + "\u001B[0m");
			}
			break;
		}
		
		return;
	}
	
	// SILO
	private void actionExploitationSilo(Location myPosition, List<Couple<Location,List<Couple<Observation,String>>>> listObservations, List<String> visibleAgents, List<String> obstacles) {
		// 3) update the map with the observations
		updateMapWithObservations(myPosition, listObservations);
		
		// 4) check if there is a visible agent we want to communicate with
		checkForCommunication(visibleAgents);

		// 5) check if we are currently communicating with an agent
		if (!this.currentCommunications.isEmpty()) {
			return;
		}
		
		// 6) update the correctSiloPosition if needed
		updateCorrectSiloPosition();
		
		// 7) test to see if we can start moving from waiting for other agents to hear about the new position
		boolean enoughTimePassed = System.currentTimeMillis() - this.phase3Time > timeBetweenPhase3_1;
		boolean allAgentsAware = this.agentsToNotifyOfNewSiloPosition.isEmpty();
		if (this.siloPositionStatus == 3 && (enoughTimePassed || allAgentsAware)) {
			// start moving to the new position even if all the other agents are not aware of it yet
			this.siloPositionStatus = 1;
			this.isStationary = false;
			this.goalDestination = this.correctSiloPosition;
			this.goalPriority = 800;
			this.pathToDestination = null;
			this.temporaryDestination = null;
			this.setSiloKnowledge(System.currentTimeMillis(), 1, this.correctSiloPosition, "-1");
			this.agentsToNotifyOfNewSiloPosition.clear();
			if (verbose) {
				System.out.println("\u001B[36m" + this.myAgent.getLocalName() + " - I am now moving to the new silo position : " + this.correctSiloPosition + "\u001B[0m");
			}
		}
		// 7) try to move towards the destination (or temporary destination if there is one)
		obstacles = getObstacles();
		String moved = moveTowardsDestination(myPosition, obstacles, listObservations);
		if (moved == "onDestination") {
			if (this.siloPositionStatus == 1) {
				// we just arrived at the new correct position
				this.siloPositionStatus = 2; // we are at the correct position
				this.isStationary = true;
				this.setSiloKnowledge(System.currentTimeMillis(), 2, this.correctSiloPosition, "-1");
			}
		}
		
	}
	
	/*
	 * The code executed at each step
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void action() {
		// declare here all the variables used in the function to make it easier to read
		Location myPosition = null;
		List<Couple<Location,List<Couple<Observation,String>>>> listObservations = null;
		List<String> visibleAgents = null;
		List<String> obstacles = null;
		List<String> visibleGolems = null;
		String nextNodeId = null;

		// 0) if this is the first time action() is called, setup the agent
		if (!setupFlag) {
			setup();	// sets up the map and adds the sharing behaviour
		}
		
		// 1) Retrieve the current position
		myPosition = getPosition();
		// if the agent is not at a location, return
		if (myPosition == null) {
			return;
		}
		
		// 1.5) attempt to open a chest, there is no reason not to try it
		attemptOpenLock(myPosition.getLocationId());

		// 2) get the observations from the current position
		HashMap<String, Object> observations = getObservations();
		// get the observations from the hashmap
		listObservations = (List<Couple<Location,List<Couple<Observation,String>>>>) observations.get("lobs");
		
		visibleAgents = (List<String>) observations.get("visibleAgents");
		obstacles = (List<String>) observations.get("obstacles");
		visibleGolems = (List<String>) observations.get("visibleGolems");
		
		// 2.1) if I see the silo, update lastSiloInteractionTimeStamp
		if (visibleAgents.contains("Silo")) {
			this.considerSiloLost = false;
			this.lastSiloInteractionTimeStamp = System.currentTimeMillis();
		}
		
		// 2.3) update treasures
		updateTreasuresFromObservation(listObservations);
		
		// 2.5) check for messages	(testing)
		// checkForMessages();
		
		// 2.5) wait for a second to make it easier to see what the agent is doing	(testing)
		if (wait) {
			wait(waitTime);
		}
		
		// 3) update the golem sightings
		updateGolemSightings(visibleGolems);
		
		// execute code corresponding to the current phase
		if (phase == 1) {
			actionExploration(myPosition, listObservations, visibleAgents, obstacles);
		} else if (phase == 2) {
			actionExploitationAgent(myPosition, listObservations, visibleAgents, obstacles);
		} else if (phase == 3) {
			actionExploitationSilo(myPosition, listObservations, visibleAgents, obstacles);
		}
	}
	
	/*
	 * Handle received maps
	 * calculate the new destination for this agent and also the sender
	 * returns the new destination for the sender (null if no new destination available)
	 */
	public List<String> newMapReceived(SerializableSimpleGraph<String, MapAttribute> sgreceived, String senderPosition, HashMap<String, TreasureInfo> receivedTreasures) {
		// 1) merge the received map with our map
		this.myMap.mergeMap(sgreceived);
		mergeTreasureInfo(receivedTreasures); // also treasures
		
		// 2) empty the current moves to destination if we are in Exploration phase
		if (this.phase == 1) {
			this.pathToDestination = null;
		}
		
		// 2.5) test for myAgent null	(should not happen)
		if (this.myAgent == null) {
			System.out.println("\u001B[31m" + "Erreur: myAgent est null dans newMapReceived(), (ie : l'agent a été fini prématûrement)" + "\u001B[0m");
			return null;
		}
		
		// 3) calculate the new destination for BOTH agents
		// no obstacles given (TODO: maybe change this later, but it should be fine for now, the obstacles might cause issues if we try to give them to)
		HashMap<String, List<String>> newDestinations = this.myMap.getPathsForTwoAgents(getPosition().getLocationId(), senderPosition, new ArrayList<String>());
		// 3.1) if we are in exploration phase, set the new destination
		if (this.phase == 1) {
			pathToDestination = newDestinations.get("agent1");
		}
		List<String> senderNewDestination = newDestinations.get("agent2");
		
		// 4) return the new destination for the sender
		return senderNewDestination;
	}
	
	
	/*
	 * method to receive a new pathToDestination, updates the pathToDestination list if it is interesting
	 */
	public void newpathToDestination(List<String> newpathToDestination) {
		// 1) if the new list is null that means the other agent has no moves to recommend
		// for now we just ignore it because our map might not be full yet
		// in practice it means we'll probably get a new map set to us soon
		if (newpathToDestination == null) {
			return;
		}

		// 2) update the pathToDestination list
		this.pathToDestination = newpathToDestination;
	}
	
	/*
	 * method to get the current priority of the agent
	 */
	public int getPriority() {
		return (this.temporaryDestination != null) ? this.temporaryPriority : this.goalPriority;
	}
	
	/*
	 * method to get the current goal priority of the agent
	 */
	public int getGoalPriority() {
		return this.goalPriority;
	}
	
	/*
	 * method to get the current destination of the agent
	 */
	public String getDestination() {
		return (this.temporaryDestination != null) ? this.temporaryDestination : this.goalDestination;
	}
	
	/*
	 * method to get the current moves to destination of the agent
	 */
	public List<String> getpathToDestination() {
		return this.pathToDestination;
	}
	
	/*
	 * getter myMap
	 */
	public MapRepresentation getMyMap() {
		return this.myMap;
	}
	
	/*
	 * getter knownTreasures
	 */
	public HashMap<String, TreasureInfo> getKnownTreasures() {
		return this.knownTreasures;
	}
	
	/*
	 * getter for the agent's silo knowledge
	 */
	public HashMap<String, Object> getSiloKnowledge() {
		return this.siloKnowledge;
	}
	
	/*
	 * setter for the agent's silo knowledge
	 */
	public void setSiloKnowledge(HashMap<String, Object> siloKnowledge) {
		this.siloKnowledge = siloKnowledge;
	}
	
	/*
	 * setter for the agent's silo knowledge using the values
	 */
	public void setSiloKnowledge(long timestamp, int positionStatus, String position, String nextPosition) {
		this.siloKnowledge = new HashMap<String, Object>();
		this.siloKnowledge.put("timestamp", timestamp);
		this.siloKnowledge.put("positionStatus", positionStatus);
		this.siloKnowledge.put("position", position);
		this.siloKnowledge.put("nextPosition", nextPosition);
	}
	
	/*
	 * setter for the agent's silo knowledge vector
	 */
	public void setSiloKnowledgeVector(String agentName, HashMap<String, Object> siloKnowledge) {
		this.SiloKnowledgeVector.put(agentName, siloKnowledge);
	}
	
	/*
	 * getter for the agent's silo knowledge vector 
	 */
	public HashMap<String,HashMap<String,Object>> getSiloKnowledgeVector() {
		return this.SiloKnowledgeVector;
	}
	
	/*
	 * getter isSiloAgent
	 */
	public boolean isSiloAgent() {
		return this.isSiloAgent;
	}
	
	/*
	 * getter isStationary
	 */
	public boolean isStationary() {
		return this.isStationary;
	}
	
	/*
	 * Set the temporary destination parameters
	 */
	public void setTemporaryDestinationParameters(List<String> path, int priority) {
		if (path == null || path.isEmpty()) {
			// current position is the destination if not given
            this.temporaryDestination = getPosition().getLocationId();
		} else {
			this.temporaryDestination = path.get(path.size() - 1);
		}
		this.temporaryPriority = priority;
		this.pathToDestination = path;
		this.isStationary = false;
	}
	
	@Override
	public boolean done() {
		return finished;
	}

}
