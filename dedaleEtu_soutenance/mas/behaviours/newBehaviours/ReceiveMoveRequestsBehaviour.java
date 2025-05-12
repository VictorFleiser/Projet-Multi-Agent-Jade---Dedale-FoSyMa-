package eu.su.mas.dedaleEtu.mas.behaviours.newBehaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.graphstream.graph.Node;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

/**
 * 
 * Receive and handles moveRequests from other agents
 */
public class ReceiveMoveRequestsBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	
	private ExploCoopBehaviour exploCoopBehaviour;
	
	private boolean verbose = false;
	
	/**
	 * @param a the agent
	 * @param exploCoopBehaviour the behaviour we need to update
	 * @param verbose whether to print information or not
	 */
	public ReceiveMoveRequestsBehaviour(Agent a, ExploCoopBehaviour exploCoopBehaviour, boolean verbose) {
		super(a);
		this.exploCoopBehaviour = exploCoopBehaviour;
		this.verbose = verbose;
		
	}

	private static final long serialVersionUID = -568863390879327961L;

	/*
	 * check for messages in the agent's inbox with the protocol SHARE-TOPO-PLAN
	 */
	private ACLMessage checkMessage() {
		MessageTemplate msgTemplate=MessageTemplate.and(
				MessageTemplate.MatchProtocol("MOVE-REQUEST"),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM));
		ACLMessage msgReceived=this.myAgent.receive(msgTemplate);
		return msgReceived;
	}

	/*
	 * get the contents of the message
	 * return the sender's name and the map received
	 * TODO: handle more message contents like the destination, etc.
	 */
	private HashMap<String, Object> getMessageContents(ACLMessage msgReceived) {
		HashMap<String, Object> messageContents = new HashMap<>();
		String senderPosition = null;
		
		// get the sender's name
		String senderName = msgReceived.getSender().getLocalName();
		
		// get the map received
		SerializableSimpleGraph<String, MapAttribute> sgreceived = null;
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, Object> contentObject = (HashMap<String, Object>) msgReceived.getContentObject();
			sgreceived = (SerializableSimpleGraph<String, MapAttribute>) contentObject.get("map");
			senderPosition = (String) contentObject.get("position");
		} catch (UnreadableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// get the message id
		String messageId = msgReceived.getConversationId();
		
		messageContents.put("senderName", senderName);
		messageContents.put("sgreceived", sgreceived);
		messageContents.put("messageId", messageId);
		messageContents.put("senderPosition", senderPosition);
		
		return messageContents;
	}
	
	/*
	 * create a behaviour to send an acknowledgment
	 */
	private void createAckBehaviour(String senderName, String messageId, HashMap<String, Object> content) {
		// 1) create the behaviour
		SendAck sendAckBehaviour = new SendAck(this.myAgent, senderName, messageId, this.exploCoopBehaviour, content, verbose);
		
		// 2) add the behaviour to the agent
		this.myAgent.addBehaviour(sendAckBehaviour);
		
		// 3) register the communication in the exploCoopBehaviour
		Couple<String, Behaviour> couple = new Couple<String, Behaviour>(senderName, sendAckBehaviour);
		
	}
	
	/*
	 * Handles what to do with the moveRequest received and the agent is stationary
	 * Separated in a different function to make the code easier to read
	 */
	public void handleMoveRequestStationary(String senderPosition, List<String> senderPath, int senderPriority, String senderName) {
		// 0) get the various parameters
		String myPosition = this.exploCoopBehaviour.getPosition().getLocationId();
		int myPriority = this.exploCoopBehaviour.getPriority();
		
		// 1) do 2 BFS to find the closest intersection :
		// 1.1) BFS1 : in the direction of the sender, we choose a node 1 further.
		// 1.1.1) setting up the correct obstacles to make sure the BFS goes towards the sender
		List<String> pathToSender = this.exploCoopBehaviour.getMyMap().getShortestPath(myPosition, senderPosition);
		List<Node> neighborNodes = this.exploCoopBehaviour.getMyMap().getNeighborNodes(myPosition);
		List<String> obstacles1 = new ArrayList<>();
		for (Node node : neighborNodes) {
			if (!pathToSender.contains(node.getId())) {
				obstacles1.add(node.getId());
			}
		}
		obstacles1.addAll(this.exploCoopBehaviour.getObstacles());	// TODO: correct ? didn't use it in the non stationary version
		
		// 1.1.2) using the BFS to find a node 1 further the 1st intersection we find
		List<String> newPath1 = this.exploCoopBehaviour.getMyMap().getShortestPathToNodeOneFurtherThanIntersectionWithObstacle(myPosition, obstacles1);
		
		// 1.2) BFS2 : in the other directions, we choose a node 1 further
		// 1.2.1) setting up the obstacles
		List<String> obstacles2 = new ArrayList<>();
		obstacles2.add(senderPosition);	// we can't go through the sender's position
		obstacles2.addAll(this.exploCoopBehaviour.getObstacles());
		
		// 1.2.2) using the BFS to find a node 1 further the 1st intersection we find
		List<String> newPath2 = this.exploCoopBehaviour.getMyMap().getShortestPathToNodeOneFurtherThanIntersectionWithObstacle(myPosition, obstacles2);
		// TODO : if the intersection is further than the sender's destination : write it down for later
		
		
		// 2) we compare the priority of our main destination (goal) with the priority of the other agent
		if (myPriority > senderPriority) {
			// 2.1) we have a higher priority, newTemporaryDestination as follows :
			int priority = this.exploCoopBehaviour.getGoalPriority();
			
			
			// find the closest intersection between BFS1 and BFS2
			if (newPath1 == null && newPath2 == null) {
				// we are stuck and can't let the other pass.
				// ideally we send them a message cantLetYouPass
				if (verbose) {
					System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
							+ " but is completely stuck, can't let you pass.");
				}
				return;
			}
			if (newPath1 == null) {
				this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath2, priority);
				
				if (verbose) {
					System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
							+ " and changed its temporary destination to the path2 " + newPath2);
				}
				return;
			}
			if (newPath2 == null) {
				this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath1, priority);

				if (verbose) {
					System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
							+ " and changed its temporary destination to the path1 " + newPath1);
				}
				return;
			}
			// both paths are not null, get the shortest path between the two
			if (newPath1.size() < newPath2.size()) {
				this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath1, priority);

				if (verbose) {
					System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
							+ " and changed its temporary destination to (path1 shorter) " + newPath1);
				}
			} else {
				this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath2, priority);

				if (verbose) {
					System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
							+ " and changed its temporary destination to (path2 shorter) " + newPath2);
				}
			}
			return;
		}
		// 2.2) we have a lower priority, newTemporaryDestination as follows :
		int priority = senderPriority;
		if (newPath2 != null) {
			this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath2, priority);

			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
						+ " and changed its temporary destination to the path2 " + newPath2);
			}
			return;
		}
		if (newPath1 != null) {
			this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath1, priority);

			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
						+ " and changed its temporary destination to the path1 " + newPath1);
			}
			return;
		}
		// we are stuck, if a node ouside the sender's path is found, we can go there, otherwise cantLetYouPass.
		List<String> obstacles = new ArrayList<>();
		obstacles.add(senderPosition);	// we can't go through the sender's position
		obstacles.addAll(this.exploCoopBehaviour.getObstacles());
		List<String> path = (List<String>) this.exploCoopBehaviour.getMyMap().getShortestPathToNodeNotInListWithObstacles(myPosition, senderPath, obstacles);
		if (path != null) {
			this.exploCoopBehaviour.setTemporaryDestinationParameters(path, priority);

			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
						+ " and changed its temporary destination to the path " + path);
			}
			return;
		}
		// we are stuck and can't let the other pass.
		// ideally we send them a message cantLetYouPass
		return;
	}
	
	/*
	 * Handles what to do with the moveRequest received
	 */
	public void handleMoveRequest(String senderPosition, List<String> senderPath, int senderPriority, String senderName) {
		// 0) if the agent is stationary, it needs to be handled differently
		if (exploCoopBehaviour.isStationary()) {
			handleMoveRequestStationary(senderPosition, senderPath, senderPriority, senderName);
			return;
		}
		
		// 1) check the priority of the sender
		int myPriority = this.exploCoopBehaviour.getPriority();
		if (senderPriority < myPriority) {
			// the sender has a lower priority, we can just ignore the message
			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
						+ " but has a lower priority, ignoring it.");
			}
			
			return;
		}
		
		// The sender has a higher priority
		
		// 2) check if we are going in a direction other than towards the sender
		String myPosition = this.exploCoopBehaviour.getPosition().getLocationId();
		List<String> myPath = this.exploCoopBehaviour.getpathToDestination(); 
		
		// COMMENTED OUT, because it might cause a deadlock if we ignore a move request (we are not going towards the sender)
		// example : A wants to go to B, B wants to go to C, C wants to go to A, everyone is stuck and no one is listening to the other

//		boolean directionTowardsSender;
//		if (myPath == null) {
//            directionTowardsSender = true;	// by default we can consider that we are going towards the sender if we don't have a path
//        } else {
//        	directionTowardsSender = myPath.contains(senderPosition);
//    	}
//		if (!directionTowardsSender) {
//			// we are not going towards the sender, we can ignore the message
//			if (verbose) {
//				System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
//						+ " but is not going towards the sender, ignoring it.");
//			}
//			
//			return;
//		}
		
//		// we are going towards the sender
		
		// 3) change the temporaryDestination :
		// 3.1) use a BFS to find the closest node that is not on the sender's path :
		
		// 3.1.1) setting up the obstacles
		List<String> obstacles = new ArrayList<>();
		HashMap<String, String> obstacleTypes = new HashMap<>();
		obstacles.add(senderPosition);	// we can't go through the sender's position
		obstacles.addAll(this.exploCoopBehaviour.getObstacles());
		obstacleTypes.put(senderPosition, "otherAgent");
		String siloPosition = this.exploCoopBehaviour.getSiloPosition();
		if (siloPosition != null) {
			obstacleTypes.put(siloPosition, "silo");
		}
		for (String obstacle : obstacles) {
			// test if the obstacle in not already in obstacleTypes -> it's a golem
			if (!obstacleTypes.containsKey(obstacle)) {
				obstacleTypes.put(obstacle, "golem");
			}
		}
		
		// List<String> newPath = this.exploCoopBehaviour.getMyMap().getShortestPathToNodeNotInListWithObstacles(myPosition, senderPath, obstacles,
		HashMap<String, Object> results = (HashMap<String, Object>) this.exploCoopBehaviour.getMyMap().getShortestPathToNodeNotInListWithObstacles(myPosition, senderPath, obstacles, obstacleTypes);
		boolean success = (boolean) results.get("success");
		List<String> newPath = (List<String>) results.get("newPath");
		String deadEndType = (String) results.get("deadEndType");
		
		
		if (success) {
			// 3.2) we found a path, we change our temporary destination
			try {
				this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath, senderPriority);
				if (verbose) {
					System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
							+ " and changed its temporary destination to " + newPath);
				}
				
				return;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("newPath : " + newPath);
			}
		}
		
		// we didn't find a path, therefore it was a dead end
		if (verbose) {
			System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
					+ " but is completely stuck, deadEndType : " + deadEndType);
		}
		// 3.2) use a BFS in the direction of the sender to find an intersection to let the sender pass
		// 3.2.1) setting up the correct obstacles to make sure the BFS goes towards the sender
		List<String> pathToSender = this.exploCoopBehaviour.getMyMap().getShortestPath(myPosition, senderPosition);
		List<Node> neighborNodes = this.exploCoopBehaviour.getMyMap().getNeighborNodes(myPosition);
		List<String> obstacles2 = new ArrayList<>();
		for (Node node : neighborNodes) {
			if (!pathToSender.contains(node.getId())) {
				obstacles2.add(node.getId());
			}
		}
		// TODO: maybe should add the other obstacles too?
		
		// 3.2.2) using the BFS to find a node 1 further the 1st intersection we find
		List<String> newPath2 = this.exploCoopBehaviour.getMyMap().getShortestPathToNodeOneFurtherThanIntersectionWithObstacle(myPosition, obstacles2);
		if (newPath2 != null) {
			// 3.2.3) we found a path, we change our temporary destination
			int priority;
			switch (deadEndType) {
			case "siloDeadEnd":
				priority = 900;
				break;
			case "golemDeadEnd":
				priority = 925;
				break;
			default:	// "deadEnd"
				priority = 950;
				break;
			}
			this.exploCoopBehaviour.setTemporaryDestinationParameters(newPath2, priority);
			
			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName
						+ " and changed its temporary destination to " + newPath2);
			}

			return;
		}
		else {
			// 3.2.4) we didn't find a path, we are completely stuck
			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " received a moveRequest from " + senderName + " but could not find a way out.");
			}
			// print currently known map
			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " currently knows the following map: ");
				MapRepresentation map = this.exploCoopBehaviour.getMyMap();
				map.printMap();
			}
		}
		
		// we didn't find a path, we are completely stuck
		// TODO: figure out what should be done
	}
	
	/*
	 * The code executed at each step
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void action() {
		// declare here all the variables used in the function to make it easier to read
		ACLMessage msgReceived = null;
		String senderPosition = null;
		List<String> senderPath = null;
		int senderPriority = -1;
		
		
		// 1) check for messages
		msgReceived = checkMessage();
		
		// 2) if no message is received, block
		if (msgReceived == null) {
			block();
			return;
		}
		
		// 3) get the contents of the message
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, Object> contentObject = (HashMap<String, Object>) msgReceived.getContentObject();
			senderPosition = (String) contentObject.get("position");
			senderPath = (List<String>) contentObject.get("path");
			senderPriority = (int) contentObject.get("priority");
		} catch (UnreadableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// 3.5) print
		if (verbose) {
			System.out.println(this.myAgent.getLocalName()+" received a moveRequest from "+msgReceived.getSender().getLocalName()+" with the following content: "+senderPosition+", "+senderPath+", "+senderPriority);
		}
        
        // 4) handle the moveRequest
        handleMoveRequest(senderPosition, senderPath, senderPriority, msgReceived.getSender().getLocalName());
	}

	@Override
	public boolean done() {
		return finished;
	}

}
