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
 * Receive communications from other agents, creates a behaviour to send an acknowledgment, and sends the new mission
 */
public class ReceiveCommunicationWithSiloBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	
	private SiloStrategyBehaviour siloStrategy;
	
	private boolean verbose = false;

	/**
	 * @param a the agent
	 * @param siloStrategy the silo strategy behaviour
	 * @param verbose whether to print information or not
	 */
	public ReceiveCommunicationWithSiloBehaviour(Agent a, SiloStrategyBehaviour siloStrategy, boolean verbose) {
		super(a);
		this.siloStrategy = siloStrategy;
		this.verbose = verbose;
		
	}

	private static final long serialVersionUID = -568863390879327961L;

	/*
	 * check for messages in the agent's inbox with the protocol STRATEGY
	 */
	private ACLMessage checkMessage() {
		MessageTemplate msgTemplate=MessageTemplate.and(
				MessageTemplate.MatchProtocol("STRATEGY"),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM));
		ACLMessage msgReceived=this.myAgent.receive(msgTemplate);
		return msgReceived;
	}

	/*
	 * get the contents of the message
	 */
	private HashMap<String, Object> getMessageContents(ACLMessage msgReceived) {
		HashMap<String, Object> messageContents = new HashMap<>();
		
		// get the sender's name
		String senderName = msgReceived.getSender().getLocalName();
		HashMap<String, Object> contents = new HashMap<>();
		
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, Object> contentObject = (HashMap<String, Object>) msgReceived.getContentObject();
			contents = contentObject;
		} catch (UnreadableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// get the message id
		String messageId = msgReceived.getConversationId();
		
		messageContents.put("senderName", senderName);
		messageContents.put("contents", contents);
		messageContents.put("messageId", messageId);
		
		return messageContents;
	}
	
	/*
	 * create a behaviour to send an acknowledgment
	 */
	private void createAckBehaviour(String senderName, String messageId, HashMap<String, Object> content) {
		// 1) create the behaviour
		SendAck sendAckBehaviour = new SendAck(this.myAgent, senderName, messageId, this.siloStrategy.baseBehaviour, content, verbose);
		
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
		String messageId = null;
		HashMap<String, Object> ACKContent = null;
		HashMap<String, Object> contents = null;
		
		
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
		messageId = (String) messageContents.get("messageId");
		contents = (HashMap<String, Object>) messageContents.get("contents");

		
		// 3.5) print (similar to the one in SiloStrategyBehaviour)
//		if (verbose) {
//			System.out.println(this.myAgent.getLocalName()+" received a communication from " + msgReceived.getSender().getLocalName() + ": " + contents);
//		}
        
        // 4) send the message to the silo strategy behaviour
        // we get back the new mission
		HashMap<String, Object> newMission = this.siloStrategy.newCommunication(contents);
		
		// 5) Create a behaviour to send an acknowledgment
        ACKContent = new HashMap<String, Object>();
        ACKContent.put("newMission", newMission);
        createAckBehaviour(senderName, messageId, ACKContent);
	}

	@Override
	public boolean done() {
		return finished;
	}

}
