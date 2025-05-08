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
 * Receive and handles missions received from the silo
 */
public class ReceiveMissonsBehaviour extends SimpleBehaviour{
	
	private boolean finished = false;
	
	private ExploCoopBehaviour exploCoopBehaviour;
	
	private boolean verbose = false;

	/**
	 * @param a the agent
	 * @param exploCoopBehaviour the behaviour we need to update
	 * @param verbose whether to print information or not
	 */
	public ReceiveMissonsBehaviour(Agent a, ExploCoopBehaviour exploCoopBehaviour, boolean verbose) {
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
				MessageTemplate.MatchProtocol("MISSION"),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM));
		ACLMessage msgReceived=this.myAgent.receive(msgTemplate);
		return msgReceived;
	}

	/*
	 * get the contents of the message
	 */
	private HashMap<String, Object> getMessageContents(ACLMessage msgReceived) {
		HashMap<String, Object> mission;
		try {
			mission = (HashMap<String, Object>) msgReceived.getContentObject();
			return mission;
		} catch (UnreadableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
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
		HashMap<String, Object> mission = getMessageContents(msgReceived);
		
		// 3.5) print
		if (verbose) {
			System.out.println(this.myAgent.getLocalName()+" received a mission from Silo slfjhsl");
			System.out.println("Mission: "+mission);
		}
        
		// 6) update the agent's mission in exploCoopBehaviour
		exploCoopBehaviour.newMission(mission);
	}

	@Override
	public boolean done() {
		return finished;
	}

}
