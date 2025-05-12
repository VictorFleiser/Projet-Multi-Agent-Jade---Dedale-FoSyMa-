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
 * Send a message to another agent to request they move out of our way.
 * Does not wait for a response or stop movement.
 */
public class SendMoveRequestBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	
	/*
	 * the agent to send the message to
	 */
	private String receiver;
	
	/*
	 * the id of the message
	 */
    private String messageId;
    
    /*
     * the current position of the agent
     */
    private String position;
    
    /*
     * the path to the destination that the agent is following
     */
    private List<String> path;
    
    /*
     * the priority of the destination
     */
    private int priority;
    
	/*
	 * flag to print information
	 */
    private boolean verbose = false;
	
	/**
	 * The agent sends the message (one shot).

	 * @param a : the agent owning this behaviour
	 * @param receiver : the agent to send the message to
	 * @param position : our position
	 * @param path : the path to the destination that we are following
	 * @param priority : the priority of our destination
	 * @param verbose whether to print information
	 */
	public SendMoveRequestBehaviour(Agent a, String receiver, String position, List<String> path, int priority, boolean verbose) {
		// 1) set the agent attributes
		super(a);
		this.receiver=receiver;
        this.messageId = "MSG-" + System.currentTimeMillis() + "-" + this.myAgent.getLocalName();	// an unique ID to identify the message
        this.position = position;
        this.path = path;
        this.priority = priority;
        this.verbose = verbose;
        a.addBehaviour(this);
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
    	// 1) create the message
    	ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
    	msg.setProtocol("MOVE-REQUEST");
    	msg.setSender(this.myAgent.getAID());
    	msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
    	msg.setConversationId(messageId);
    	
    	// 2) create the content
    	HashMap<String, Object> content = new HashMap<>();
    	content.put("position", position);
    	content.put("path", path);
    	content.put("priority", priority);
    	
    	// 3) set the content
    	try {
    		msg.setContentObject(content);
        } catch (IOException e) {
        	// TODO Auto-generated catch block
        	e.printStackTrace();
    	}
    	
		// 4) send the message
		((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
		
		// 5) print
		if (verbose) {
			System.out.println(this.myAgent.getLocalName() + " sent a MOVE-REQUEST to " + receiver + " with the following content:" + content);
		}
		
		// 6) end the behaviour
		finished = true;
    }
    
    @Override
	public boolean done() {
		return finished;
	}

}
