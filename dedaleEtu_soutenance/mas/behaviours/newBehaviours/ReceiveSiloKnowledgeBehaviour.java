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
 * Receive silo knowledge from other agents, creates a behaviour to send an acknowledgment, and sends the silo knowledge to the exploCoopBehaviour to handle it.
 */
public class ReceiveSiloKnowledgeBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	
	private ExploCoopBehaviour exploCoopBehaviour;
	
	private boolean verbose = false;

	/**
	 * @param a the agent
	 * @param exploCoopBehaviour the behaviour which will handle the silo knowledge received
	 * @param verbose whether to print information or not
	 */
	public ReceiveSiloKnowledgeBehaviour(Agent a, ExploCoopBehaviour exploCoopBehaviour, boolean verbose) {
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
				MessageTemplate.MatchProtocol("SHARE-SILO-KNOWLEDGE"),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM));
		ACLMessage msgReceived=this.myAgent.receive(msgTemplate);
		return msgReceived;
	}

	/*
	 * get the contents of the message
	 * return the sender's name and the silo knowledge received
	 */
	private HashMap<String, Object> getMessageContents(ACLMessage msgReceived) {
		HashMap<String, Object> messageContents = new HashMap<>();
		
		// get the sender's name
		String senderName = msgReceived.getSender().getLocalName();
		
		// get the silo knowledge received
		HashMap<String, Object> siloKnowledge = new HashMap<>();
		
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, Object> contentObject = (HashMap<String, Object>) msgReceived.getContentObject();
			siloKnowledge = (HashMap<String, Object>) contentObject.get("siloKnowledge");
		} catch (UnreadableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// get the message id
		String messageId = msgReceived.getConversationId();
		
		messageContents.put("senderName", senderName);
		messageContents.put("siloKnowledge", siloKnowledge);
		messageContents.put("messageId", messageId);
		
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
		HashMap<String, Object> siloKnowledge = null;
		String messageId = null;
		HashMap<String, Object> ACKContent = null;
		
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
		siloKnowledge = (HashMap<String, Object>) messageContents.get("siloKnowledge");
		messageId = (String) messageContents.get("messageId");

		// 3.5) print
		if (verbose) {
			System.out.println(this.myAgent.getLocalName()+" received a silo knowledge from "+senderName);
		}
        
        // 4) test if the our silo knowledge is outdated compared to the one received (so arguments are inverted in the method otherAgentSiloKnowledgeIsOutdated())
		boolean updateOurSiloKnowledge = this.exploCoopBehaviour.otherAgentSiloKnowledgeIsOutdated(siloKnowledge, this.exploCoopBehaviour.getSiloKnowledge()); 
		if (updateOurSiloKnowledge) {
			// 4.1) update the silo knowledge
			this.exploCoopBehaviour.setSiloKnowledge(siloKnowledge);
			// 4.2) update the silo knowledge of the receiver
			this.exploCoopBehaviour.setSiloKnowledgeVector(senderName, siloKnowledge);
			
			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " updated its silo knowledge with the one received from " + senderName);
				System.out.println(this.myAgent.getLocalName() + " silo knowledge: " + this.exploCoopBehaviour.getSiloKnowledge());
			}
		}
		
		// 5) Create a behaviour to send an acknowledgment
        ACKContent = new HashMap<String, Object>();
        createAckBehaviour(senderName, messageId, ACKContent);
	}

	@Override
	public boolean done() {
		return finished;
	}

}
