package eu.su.mas.dedaleEtu.mas.behaviours.newBehaviours;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

/**
 * Share the map with another agent, then create a behaviour to wait for an acknowledgment.
 */
public class ShareMapBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	private MapRepresentation mapToShare;
	private String receiver;
    private String messageId;
    private WaitForAckBehaviour waitForAckBehaviour;
    private ExploCoopBehaviour exploCoopBeehaviour;
    private HashMap<String, Object> content;	// additional content to share
    private boolean verbose = false;
	
	/**
	 * The agent share its map (one shot).

	 * @param a the agent
	 * @param receiver the agent to share the map with
	 * @param mapToShare the map to share
	 * @param exploCoopBeehaviour the behaviour that we need to update currentCommunications
	 * @param content the additional content to share
	 * @param verbose whether to print information
	 */
	public ShareMapBehaviour(Agent a, String receiver, MapRepresentation mapToShare, ExploCoopBehaviour exploCoopBeehaviour, HashMap<String, Object> content, boolean verbose) {
		// 1) set the agent attributes
		super(a);
		this.mapToShare=mapToShare.copy();
		this.receiver=receiver;
		this.exploCoopBeehaviour = exploCoopBeehaviour;
        this.messageId = "MSG-" + System.currentTimeMillis() + "-" + this.myAgent.getLocalName();	// an unique ID to identify the message
        this.content = content;
        this.verbose = verbose;
		if (verbose) {
			System.out.println(this.myAgent.getLocalName() + " created a new message, id: " + messageId);
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -568863390879327961L;

	/*
	 * check if we need to send the message or if we already sent it and are now waiting for an acknowledgment
	 */
    @Override
	public void action() {
    	// 1) check if we sent the message already
		if (this.waitForAckBehaviour == null) {
			
			// 2) send the map
            sendMap();

            // 3) create the behaviour to wait for an acknowledgment
            this.waitForAckBehaviour = new WaitForAckBehaviour(this.myAgent, receiver, messageId, verbose);
            
        } else {
        	
			// 2) check if we received the acknowledgment
			if (waitForAckBehaviour.done()) {
				handleAckResponse();
			}
        }
    }

    /*
     * send the map to the receiver
     */
	private void sendMap() {
		// 1) create the message template
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("SHARE-TOPO");
        msg.setSender(this.myAgent.getAID());
        msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        msg.setConversationId(messageId);

        // 2) set the contents of the message
        SerializableSimpleGraph<String, MapAttribute> mapToSend = mapToShare.getSerializableGraph();
        HashMap<String, Object> contentObject = new HashMap<>();
        contentObject.put("map", mapToSend);
        for (String key : content.keySet()) {
        	// add the additional content to the message
        	contentObject.put(key, content.get(key));
    	}
        try {
			msg.setContentObject(contentObject);
		} catch (IOException e) {
			e.printStackTrace();
		}

        
        // 3) send the message
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
        
        // 3.5) print	(testing)
		if (verbose) {
			System.out.println(this.myAgent.getLocalName() + " sent its map to " + receiver);
		}
    }
    
	/*
	 * handle the acknowledgment response
	 */
    private void handleAckResponse() {
    	// 1) get the response
    	Couple<String, Object> waitForAckBehaviourReturn = waitForAckBehaviour.getWaitForAckBehaviourReturn();

    	// 2) handle the response based on the type of response (ACK, NACK, TIMEOUT)
    	String response = (String) waitForAckBehaviourReturn.getLeft();
    	if (response == "ACK") {
    		// 3) if we received a ACK, merge the agent's map in exploCoopBehaviour with the map we sent
    		MapRepresentation agentMap = exploCoopBeehaviour.agentMaps.get(receiver);
    		agentMap.mergeMap(mapToShare.getSerializableGraph());
    		
    		// 4) reset the agent's updatesSinceLastCommunication in exploCoopBehaviour
    		//exploCoopBeehaviour.updatesSinceLastCommunication.put(receiver, 0);
    		exploCoopBeehaviour.lastCommunicationTime.put(receiver, System.currentTimeMillis());
    		
    		// 4.5) print		(testing)
    		// System.out.println(this.myAgent.getLocalName() + " received ACK from " + receiver);
    	}
    	else if (response == "TIMEOUT" || response == "NACK") {
    		// 3) print		(testing)
			if (verbose) {
				System.out.println(this.myAgent.getLocalName() + " timed out (or nack) waiting for ACK from " + receiver);
			}
    	}
    	// 5) handle the content of the message
    	@SuppressWarnings("unchecked")
		HashMap<String, Object> content = (HashMap<String, Object>) waitForAckBehaviourReturn.getRight();
		if (content != null) {
			// handle the content
			@SuppressWarnings("unchecked")
			List<String> movesToDestination = (List<String>) content.get("senderNewMoves");
			
			// 6) update the agent's movesToDestination in exploCoopBehaviour
			exploCoopBeehaviour.newpathToDestination(movesToDestination);
			
			if (verbose) {
				System.out.println("\u001B[35m" + this.myAgent.getLocalName() + " received new move recomendations from " + receiver + ": " + movesToDestination + "\u001B[0m");
			}
		}
    	
    	// 5) remove the communication from exploCoopBehaviour
    	exploCoopBeehaviour.currentCommunications.removeIf(couple -> couple.getLeft().equals(receiver) && couple.getRight().equals(this));
    	
    	// 6) cleanup and end the behaviour
    	mapToShare = null;
    	waitForAckBehaviour = null;
        finished = true;
    }
    
    @Override
	public boolean done() {
		return finished;
	}

}
