package eu.su.mas.dedaleEtu.mas.behaviours.newBehaviours;

import java.io.IOException;
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
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

/**
 * waits for an acknowledgment from another agent, then returns the acknowledgment and the content of the message
 * has a timeout of 5 seconds
 */
public class WaitForAckBehaviour extends SimpleBehaviour{

	/**
	 * 
	 */
	private static final long serialVersionUID = -568863390879327961L;

	
	private boolean finished = false;
	
    private String receiver;
    private String id;
    private Couple<String, Object> waitForAckBehaviourReturn = null;
    private long timeout;
    private boolean verbose = false;

    /*
     * check every 100 milliseconds for a message with the correct attributes
     */
    private TickerBehaviour ticker;

    /**
	 * The agent receives maps and updates its map.

	 * @param a the agent
	 * @param receiver the agent to receive the acknowledgment from
	 * @param id the conversation ID
	 * @param verbose whether to print information
	 */
    public WaitForAckBehaviour(Agent a, String receiver, String id, boolean verbose) {
    	// 1) set the agent attributes
        super(a);
        this.receiver = receiver;
        this.id = id;
        this.timeout = System.currentTimeMillis() + 5000; // 5-second timeout
        this.verbose = verbose;
        
        // 2) create the ticker behaviour
        // TODO: maybe it should use a block in action() instead
        this.ticker = new TickerBehaviour(a, 100) { // Check every 100 milliseconds
            @Override
            protected void onTick() {
                checkMessage();
            }
        };
        
        // 3) add the ticker behaviour to the agent
        // TODO: should we also add "this" behaviour to the agent?
        // also how is getWaitForAckBehaviourReturn() available even after we are finished ? the object is probably not destroyed when the behaviour is done
        // in which case it is probably removed by the garbage collector once all references to it are removed
        a.addBehaviour(ticker);
    }

    /*
     * check for messages in the agent's inbox with the protocol ACK and the correct conversation ID
     */
    private void checkMessage() {
    	// 1) set the message template
        MessageTemplate msgTemplate = MessageTemplate.and(
        	MessageTemplate.or(
    			MessageTemplate.MatchProtocol("ACK"),
    			MessageTemplate.MatchProtocol("NACK")
			),
            MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId(id)
            )
        );
        
        // 2) check for messages in the agent's inbox
        ACLMessage msgReceived = myAgent.receive(msgTemplate);

        // 3) handle the message if it exists, otherwise check for a timeout
        if (msgReceived != null) {
        	// 4) return the contents of the message through waitForAckBehaviourReturn
        	if (verbose) {
        		System.out.println(myAgent.getLocalName() + " received " + msgReceived.getProtocol() + " from " + receiver + " (inside WaitForAckBehaviour)");
        	}
        	try {
				waitForAckBehaviourReturn = new Couple<String, Object>(msgReceived.getProtocol(), msgReceived.getContentObject());
			} catch (UnreadableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
        	// 5) cleanup and finish the behaviour
            finished = true;
            ticker.stop(); // Stop the ticker
            ticker = null;
            
        } else if (System.currentTimeMillis() > timeout) {
        	// 4) return "TIMEOUT" through waitForAckBehaviourReturn
        	if (verbose) {
        		System.out.println(myAgent.getLocalName() + " TIMEOUT waiting for ACK from " + receiver + " (inside WaitForAckBehaviour)");
        	}
            waitForAckBehaviourReturn = new Couple<String, Object>("TIMEOUT", null);
            finished = true;
            ticker.stop(); // Stop the ticker
        }
    }

    @Override
    public void action() {
        // Do nothing here, the ticker handles the message checking
    }

    @Override
    public boolean done() {
        return finished;
    }

    /*
     * return the acknowledgment and the content of the message
     */
    public Couple<String, Object> getWaitForAckBehaviourReturn() {
        return waitForAckBehaviourReturn;
    }
}