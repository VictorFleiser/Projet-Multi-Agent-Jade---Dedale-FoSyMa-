package eu.su.mas.dedaleEtu.mas.behaviours.newBehaviours;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.TreasureInfo;
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
 * Receive maps from other agents, creates a behaviour to send an acknowledgment, and sends the map to the exploCoopBehaviour to handle it.
 */
public class ReceiveMapBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	
	private ExploCoopBehaviour exploCoopBehaviour;
	
	private boolean verbose = false;

	/**
	 * @param a the agent
	 * @param exploCoopBehaviour the behaviour which will handle the map received
	 * @param verbose whether to print information or not
	 */
	public ReceiveMapBehaviour(Agent a, ExploCoopBehaviour exploCoopBehaviour, boolean verbose) {
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
				MessageTemplate.MatchProtocol("SHARE-TOPO"),
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
		
		// get the map/treasure received
		SerializableSimpleGraph<String, MapAttribute> sgreceived = null;
		HashMap<String, TreasureInfo> treasuresReceived = null;
		
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, Object> contentObject = (HashMap<String, Object>) msgReceived.getContentObject();
			sgreceived = (SerializableSimpleGraph<String, MapAttribute>) contentObject.get("map");
			treasuresReceived = (HashMap<String, TreasureInfo>) contentObject.get("treasures");
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
		messageContents.put("treasures", treasuresReceived);
		
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
	 * The code executed at each step
	 */
	@Override
	public void action() {
		// declare here all the variables used in the function to make it easier to read
		ACLMessage msgReceived = null;
		HashMap<String, Object> messageContents = null;
		String senderName = null;
		SerializableSimpleGraph<String, MapAttribute> sgreceived = null;
		String messageId = null;
		HashMap<String, Object> ACKContent = null;
		String senderPosition = null;
		HashMap<String, TreasureInfo> treasuresReceived = null;
		
		
		// 1) check for messages
		msgReceived = checkMessage();
		
		// 2) if no message is received, block
		if (msgReceived == null) {
			block();
			return;
		}
		
		// 3) if a message is received get its contents
		messageContents = getMessageContents(msgReceived);
		// get the contents out of the hashmap
		senderName = (String) messageContents.get("senderName");
		sgreceived = (SerializableSimpleGraph<String, MapAttribute>) messageContents.get("sgreceived");
		messageId = (String) messageContents.get("messageId");
		senderPosition = (String) messageContents.get("senderPosition");
		treasuresReceived = (HashMap<String, TreasureInfo>) messageContents.get("treasures");
		
		// 3.5) print
		if (verbose) {
//			System.out.println(this.myAgent.getLocalName()+" received a map and treasure info from "+senderName);
			System.out.println(this.myAgent.getLocalName()+" received a map" + (treasuresReceived != null ? " and treasure info ("+treasuresReceived.size()+" items)" : "") + " from "+senderName);
		}
        
        // 4) send the map to the exploCoopBehaviour to handle it
        // we get back the new moves to send to the sender
		List<String> senderNewMoves = this.exploCoopBehaviour.newMapReceived(sgreceived, senderPosition, treasuresReceived);
		
		// 5) Create a behaviour to send an acknowledgment
        ACKContent = new HashMap<String, Object>();
        ACKContent.put("senderNewMoves", senderNewMoves);
        createAckBehaviour(senderName, messageId, ACKContent);
	}

	@Override
	public boolean done() {
		return finished;
	}

}
