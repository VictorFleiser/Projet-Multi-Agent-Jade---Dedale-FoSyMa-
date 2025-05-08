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
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

/**
 * The agent sends an acknowledgment to another agent.
 */
public class SendAck extends SimpleBehaviour{
	
	private boolean finished = false;
	private String receiver;
    private String messageId;
    private ExploCoopBehaviour exploCoopBeehaviour;
    private HashMap<String, Object> content;
    private boolean verbose = false;

	
	
	/**
	 * The agent share its map (one shot).
	 * 
	 * @param a         the agent sending the message
	 * @param receiver	the agent to send the acknowledgment to
	 * @param id        the id of the message
	 * @param exploCoopBeehaviour to remove the entry from currentCommunications 
	 * @param content   the additional content of the message
	 * @param verbose   whether to print information
	 * 
	 */
	public SendAck(Agent a, String receiver, String id, ExploCoopBehaviour exploCoopBeehaviour, HashMap<String, Object> content, boolean verbose) {
		super(a);
		this.receiver=receiver;
		this.messageId = id;
		this.exploCoopBeehaviour = exploCoopBeehaviour;
		this.content = content;
		this.verbose = verbose;
	}

	private static final long serialVersionUID = -568863390879327961L;

	/*
	 * create the message
	 * TODO: handle the way content is handled
	 */
	private ACLMessage createMessage() {
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		msg.setProtocol("ACK");
		msg.setSender(this.myAgent.getAID());
		msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
		msg.setConversationId(messageId);
		try {
			msg.setContentObject(content);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return msg;
	}
	
	/*
	 * remove the this entry from exploCoopBeehaviour.currentCommunications
	 */
	private void removeEntry() {
		for (Couple<String, Behaviour> entry : exploCoopBeehaviour.currentCommunications) {
			if (entry.getLeft().equals(receiver) && entry.getRight().equals(this)) {
				exploCoopBeehaviour.currentCommunications.remove(entry);
				break;
			}
		}
	}
	
	/*
	 * The code executed at execution (one shot)
	 */
	@Override
	public void action() {
		// 1) create the message
		ACLMessage msg = createMessage();
		
		// 2) send the message
		((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
		
		// 2.5) print
		if (verbose) {
			System.out.println(this.myAgent.getLocalName() + " sent an ACK to " + receiver);
		}
		
		// 3) remove the this entry from exploCoopBeehaviour.currentCommunications
        removeEntry();
        
        // 4) set finished to true
		finished = true;
    }

    
    @Override
	public boolean done() {
		return finished;
	}

}
