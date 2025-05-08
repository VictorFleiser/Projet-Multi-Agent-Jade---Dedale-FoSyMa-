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
 * Share information to the silo.
 */
public class CommunicateWithSiloBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	private String receiver;
    private String messageId;
    private WaitForAckBehaviour waitForAckBehaviour;
    private ExploCoopBehaviour exploCoopBeehaviour;
    private HashMap<String, Object> content;	// content to share
    private boolean verbose = false;
	
	/**
	 * The agent share its map (one shot).

	 * @param a the agent
	 * @param receiver the silo
	 * @param exploCoopBeehaviour the base behaviour
	 * @param content the content to share
	 * @param verbose whether to print information
	 */
	public CommunicateWithSiloBehaviour(Agent a, String receiver, ExploCoopBehaviour exploCoopBeehaviour, HashMap<String, Object> content, boolean verbose) {
		// 1) set the agent attributes
		super(a);
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
            sendContent();

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
	private void sendContent() {
		// 1) create the message template
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("STRATEGY");
        msg.setSender(this.myAgent.getAID());
        msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        msg.setConversationId(messageId);

        // 2) set the contents of the message
        HashMap<String, Object> contentObject = new HashMap<>();
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
			System.out.println(this.myAgent.getLocalName() + " sent the communication to " + receiver);
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
    		// 3) if we received a ACK, get the newMission from the message contents
    		//agentMap.mergeMap(mapToShare.getSerializableGraph());
    		
    		// 4) reset the agent's updatesSinceLastCommunication in exploCoopBehaviour
    		//exploCoopBeehaviour.updatesSinceLastCommunication.put(receiver, 0);
    		exploCoopBeehaviour.lastCommunicationTime.put(receiver, System.currentTimeMillis());
    		
    		// 4.5) print		(testing)
    		if (verbose) {
    			System.out.println(this.myAgent.getLocalName() + " received ACK from " + receiver);
    		}
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
			HashMap<String, Object> newMission = (HashMap<String, Object>) content.get("newMission");
			
			// 6) update the agent's mission in exploCoopBehaviour
			exploCoopBeehaviour.newMission(newMission);
			
			if (verbose) {
				System.out.println("\u001B[35m" + this.myAgent.getLocalName() + " received new mission from " + receiver + "\u001B[0m");
				System.out.println(this.myAgent.getLocalName() + " new mission: " + newMission);
			}
		}
    	
    	// 5) remove the communication from exploCoopBehaviour
    	exploCoopBeehaviour.currentCommunications.removeIf(couple -> couple.getLeft().equals(receiver) && couple.getRight().equals(this));
    	
    	// 6) cleanup and end the behaviour
    	//mapToShare = null;
    	waitForAckBehaviour = null;
        finished = true;
    }
    
    @Override
	public boolean done() {
		return finished;
	}

}
