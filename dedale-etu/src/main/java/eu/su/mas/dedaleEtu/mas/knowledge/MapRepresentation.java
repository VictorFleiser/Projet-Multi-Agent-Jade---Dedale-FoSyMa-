package eu.su.mas.dedaleEtu.mas.knowledge;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeRejectedException;
import org.graphstream.graph.ElementNotFoundException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.Viewer.CloseFramePolicy;

import dataStructures.serializableGraph.*;
import dataStructures.tuple.Couple;
import javafx.application.Platform;

/**
 * This simple topology representation only deals with the graph, not its content.</br>
 * The knowledge representation is not well written (at all), it is just given as a minimal example.</br>
 * The viewer methods are not independent of the data structure, and the dijkstra is recomputed every-time.
 * 
 * @author hc
 */
public class MapRepresentation implements Serializable {

	/**
	 * A node is open, closed, or agent
	 * @author hc
	 *
	 */

	public enum MapAttribute {	
		agent,open,closed;
	}

	private static final long serialVersionUID = -1333959882640838272L;

	/*********************************
	 * Parameters for graph rendering
	 ********************************/

	private String defaultNodeStyle= "node {"+"fill-color: red;"+" size-mode:fit;text-alignment:under; text-size:14;text-color:white;text-background-mode:rounded-box;text-background-color:red;}";
	private String nodeStyle_open = "node.agent {"+"fill-color: forestgreen;"+"}";
	private String nodeStyle_agent = "node.open {"+"fill-color: blue;"+"}";
	private String nodeStyle=defaultNodeStyle+nodeStyle_agent+nodeStyle_open;

	private Graph g; //data structure non serializable
	private Viewer viewer; //ref to the display,  non serializable
	private Integer nbEdges;//used to generate the edges ids

	private SerializableSimpleGraph<String, MapAttribute> sg;//used as a temporary dataStructure during migration


	public MapRepresentation() {
		//System.setProperty("org.graphstream.ui.renderer","org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		System.setProperty("org.graphstream.ui", "javafx");
		this.g= new SingleGraph("My world vision");
		this.g.setAttribute("ui.stylesheet",nodeStyle);
		this.g.setAttribute("ui.title", "Your Title");	// does not work

		Platform.runLater(() -> {
			openGui();
		});
		//this.viewer = this.g.display();

		this.nbEdges=0;
	}

	public MapRepresentation(String style) {		
		//System.setProperty("org.graphstream.ui.renderer","org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		System.setProperty("org.graphstream.ui", "javafx");
		this.g= new SingleGraph("My world vision");
		this.g.setAttribute("ui.stylesheet",nodeStyle+style);
		this.g.setAttribute("ui.title", "Your Title");	// does not work

		Platform.runLater(() -> {
			openGui();
		});
		//this.viewer = this.g.display();

		this.nbEdges=0;
	}
	
	/*
	 * Constructor with a Boolean to not open the GUI
	 */
	public MapRepresentation(Boolean openGui) {
		// System.setProperty("org.graphstream.ui.renderer","org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		System.setProperty("org.graphstream.ui", "javafx");
		this.g = new SingleGraph("My world vision");
		this.g.setAttribute("ui.stylesheet", nodeStyle);
		this.g.setAttribute("ui.title", "Your Title"); // does not work

		if (openGui) {
			Platform.runLater(() -> {
				openGui();
			});
		}
		// this.viewer = this.g.display();

		this.nbEdges = 0;
	}
	
	/*
	 * Constructor using a Serializable to create the map
	 */
	public MapRepresentation(SerializableSimpleGraph<String, MapAttribute> sgreceived) {
	    System.setProperty("org.graphstream.ui", "javafx");
	    this.g = new SingleGraph("My world vision");
	    this.g.setAttribute("ui.stylesheet", nodeStyle);
	    this.nbEdges = 0;

	    // Open GUI
	    Platform.runLater(() -> openGui());

	    // Convert nodes from sgreceived
	    for (SerializableNode<String, MapAttribute> node : sgreceived.getAllNodes()) {
	        this.addNode(node.getNodeId(), node.getNodeContent());
	    }

	    // Convert edges from sgreceived
	    for (SerializableNode<String, MapAttribute> node : sgreceived.getAllNodes()) {
	        for (String neighborId : sgreceived.getEdges(node.getNodeId())) {
	            this.addEdge(node.getNodeId(), neighborId);
	        }
	    }
	}
	
	/**
	 * Add or replace a node and its attribute 
	 * @param id unique identifier of the node
	 * @param mapAttribute attribute to process
	 */
	public synchronized void addNode(String id,MapAttribute mapAttribute){
		Node n;
		if (this.g.getNode(id)==null){
			n=this.g.addNode(id);
		}else{
			n=this.g.getNode(id);
		}
		n.clearAttributes();
		n.setAttribute("ui.class", mapAttribute.toString());
		n.setAttribute("ui.label",id);
		
		//System.out.println("Node added: " + id + " ui.class: " + );
		
	}
	
	/**
	 * Add or replace a node and its attribute
	 * Returns true if the node was added, false if it was already present
	 * @param id unique identifier of the node
	 * @param mapAttribute attribute to process
	 */
	public synchronized boolean addNodeWithAnswer(String id,MapAttribute mapAttribute){
		Node n;
        if (this.g.getNode(id)==null){
            n=this.g.addNode(id);
            n.clearAttributes();
            n.setAttribute("ui.class", mapAttribute.toString());
            n.setAttribute("ui.label",id);
            return true;
        }else{
        	n=this.g.getNode(id);
        	n.clearAttributes();
        	n.setAttribute("ui.class", mapAttribute.toString());
        	n.setAttribute("ui.label",id);
            return false;	
        }
	}

	/**
	 * Add a node to the graph. Do nothing if the node already exists.
	 * If new, it is labeled as open (non-visited)
	 * @param id id of the node
	 * @return true if added
	 */
	public synchronized boolean addNewNode(String id) {
		if (this.g.getNode(id)==null){
			addNode(id,MapAttribute.open);
			return true;
		}
		return false;
	}

	/**
	 * Add an undirect edge if not already existing.
	 * @param idNode1 unique identifier of node1
	 * @param idNode2 unique identifier of node2
	 */
	public synchronized void addEdge(String idNode1,String idNode2){
		this.nbEdges++;
		try {
			this.g.addEdge(this.nbEdges.toString(), idNode1, idNode2);
		}catch (IdAlreadyInUseException e1) {
			System.err.println("ID existing");
			System.exit(1);
		}catch (EdgeRejectedException e2) {
			this.nbEdges--;
		} catch(ElementNotFoundException e3){

		}
	}
	
	// add an edge to the graph (addNewEdge), returns true if it was a new edge

	/**
	 * Compute the shortest Path from idFrom to IdTo. The computation is currently not very efficient
	 * 
	 * 
	 * @param idFrom id of the origin node
	 * @param idTo id of the destination node
	 * @return the list of nodes to follow, null if the targeted node is not currently reachable
	 */
	public synchronized List<String> getShortestPath(String idFrom,String idTo){
		List<String> shortestPath=new ArrayList<String>();

		Dijkstra dijkstra = new Dijkstra();//number of edge
		dijkstra.init(g);
		dijkstra.setSource(g.getNode(idFrom));
		dijkstra.compute();//compute the distance to all nodes from idFrom
		List<Node> path=dijkstra.getPath(g.getNode(idTo)).getNodePath(); //the shortest path from idFrom to idTo
		Iterator<Node> iter=path.iterator();
		while (iter.hasNext()){
			shortestPath.add(iter.next().getId());
		}
		dijkstra.clear();
		if (shortestPath.isEmpty()) {//The openNode is not currently reachable
			return null;
		}else {
			shortestPath.remove(0);//remove the current position
		}
		return shortestPath;
	}

	public List<String> getShortestPathToClosestOpenNode(String myPosition) {
		//1) Get all openNodes
		List<String> opennodes=getOpenNodes();

		//2) select the closest one
		List<Couple<String,Integer>> lc=
				opennodes.stream()
				.map(on -> (getShortestPath(myPosition,on)!=null)? new Couple<String, Integer>(on,getShortestPath(myPosition,on).size()): new Couple<String, Integer>(on,Integer.MAX_VALUE))//some nodes my be unreachable if the agents do not share at least one common node.
				.collect(Collectors.toList());

		Optional<Couple<String,Integer>> closest=lc.stream().min(Comparator.comparing(Couple::getRight));
		
		//3) Compute shorterPath
		if (closest.isPresent()) {
			return getShortestPath(myPosition, closest.get().getLeft());
		}
		return null;
	}

	
	/*
	 * Returns the edges of a node
	 */
	public List<Edge> getNeighborEdges(String nodeId) {
		// 1) declare a list to store the edges
		List<Edge> edges = new ArrayList<>();
		
		// 2) get the edges of the node
		for (Edge edge : g.edges().collect(Collectors.toList())) {
			// 3) add the edge to the list if it is connected to the node
			if (edge.getNode0().getId().equals(nodeId) || edge.getNode1().getId().equals(nodeId)) {
				edges.add(edge);
			}
		}
		
		// 4) return the edges
		return edges;
	}
	
	/*
	 * Returns the neighbor nodes of a node
	 */
	public List<Node> getNeighborNodes(String nodeId) {
		// 1) declare a list to store the neighbors and edges
		List<Node> neighbors = new ArrayList<>();
		Node node = this.g.getNode(nodeId);
		List<Edge> neighborEdges = new ArrayList<>();

		// 2) get the neighbor edges of the node
		neighborEdges = getNeighborEdges(nodeId);
		
		// 3) get the neighbors corresponding to each edge
		for (Edge edge : neighborEdges) {
			Node neighbor = edge.getOpposite(node);
			neighbors.add(neighbor);
		}
		return neighbors;
	}
	
	/*
	 * Returns the shortest path to the closest open node that is accessible given a set of obstacles
	 * Uses Breadth First Search to find the shortest path
	 * returns null if no open node is accessible
	 */
	public List<String> getShortestPathToClosestOpenNodeWithObstacles(String myPosition, List<String> obstacles) {
		// 1) Initialize BFS
	    Queue<List<String>> queue = new LinkedList<>();	// Queue for storing paths
	    Set<String> visited = new HashSet<>();	// Set for storing visited nodes
	    
	    // 2) Initialize BFS with the starting position
		List<String> startPath = new ArrayList<>();
		startPath.add(myPosition);
		queue.add(startPath);
	    visited.add(myPosition);
	    
	    // 3) Run BFS
	    while (!queue.isEmpty()) {
	    	// 4) Get the current shortest path
	        List<String> path = queue.poll();
	        String currentNode = path.get(path.size() - 1);

	        // 5) Check if it's an open node
	        if (getOpenNodes().contains(currentNode)) {
	            path.remove(0); // Remove starting position
	            return path; // Return shortest path to closest open node
	        }

	        // 6) Expand neighbors
	        List<String> neighbors = getNeighborNodes(currentNode).stream().map(Node::getId).collect(Collectors.toList());
	        for (String neighbor :  neighbors) {

	            // Ignore obstacles and already visited nodes
	            if (!visited.contains(neighbor) && !obstacles.contains(neighbor)) {
	                visited.add(neighbor);
	                
	                // Create a new path and add the neighbor
	                List<String> newPath = new ArrayList<>(path);
	                newPath.add(neighbor);
	                queue.add(newPath);
	            }
	        }
	    }

	    // No open node was found
	    return null;
	}

	/*
	 * Returns the shortest path to the closest open node that is accessible given a set of obstacles
	 * FOR 2 AGENTS AT THE SAME TIME :
	 * - Simultaneous BFS for both agents :
	 * -	- Both agents explore the graph at the same time using separate queues.
	 * - Avoiding the Other Agent's Position :
	 * -	- The other agent's starting position is treated as a soft obstacle (avoided unless necessary).
	 * -	- If both agents start in the same position (due to time delay between message sent/read), the avoidance rule is ignored.
	 * - Claiming Open Nodes :
	 * -	- When an agent finds an open node, it claims it.
	 * -	- The other agent must continue searching for a different open node.
	 * -    - Edge Case: If no other open nodes exist, both agents are allowed to use the same node.
	 */
	public HashMap<String, List<String>> getPathsForTwoAgents(String myPosition, String otherAgentPosition, List<String> obstacles) {
		// 1) If both agents start at the same position, ignore avoidance rule (same if other agent's position is unknown)
		if (otherAgentPosition == null) {
			otherAgentPosition = myPosition;
		}
		boolean sameStart = myPosition.equals(otherAgentPosition);
	    
	    // 2) Initialize BFS structures for both agents
	    Queue<List<String>> queue1 = new LinkedList<>();
	    Queue<List<String>> queue2 = new LinkedList<>();
	    Queue<List<String>> queue1_unoptimal = new LinkedList<>();	// Queue for storing paths going through the other agent's position
	    Queue<List<String>> queue2_unoptimal = new LinkedList<>();	// Queue for storing paths going through the other agent's position
	    Set<String> visited1 = new HashSet<>();
	    Set<String> visited2 = new HashSet<>();
	    boolean done1 = false;
	    boolean done2 = false;
	    
	    // 3) Initialize open node tracking
	    Set<String> claimedNodes = new HashSet<>();
	    
	    // 4) Start BFS for both agents
		List<String> startPath = new ArrayList<>();
		startPath.add(myPosition);
		queue1.add(startPath);
	    visited1.add(myPosition);

	    List<String> startPath2 = new ArrayList<>();
	    startPath2.add(otherAgentPosition);
	    queue2.add(startPath2);
	    visited2.add(otherAgentPosition);
	    
	    // 5) Paths for both agents
	    List<String> path1 = null;
	    List<String> path2 = null;
	    List<String> path1_backup = null;	// Backup path for agent 1 if the only open node is claimed by agent 2
	    List<String> path2_backup = null;	// Backup path for agent 2 if the only open node is claimed by agent 1
	    
	    // 6) BFS Search Loop (Both agents explore together)
	    while (!done1 || !done2) {
	    	
	    	// 6.1) updating the done status
	    	if ((queue1.isEmpty() && queue1_unoptimal.isEmpty()) || path1 != null) {	// if the queue is empty or the path is found
	    		done1 = true;
	    	}
	    	if ((queue2.isEmpty() && queue2_unoptimal.isEmpty()) || path2 != null) {	// if the queue is empty or the path is found
	    		done2 = true;
	    	}

	        // 6.2) Agent 1 Search Step
	        if (!done1) {
	        	boolean unoptimal = false;
	            List<String> currentPath = queue1.poll();	// Get the current shortest path
				if (currentPath == null) { // if the queue is empty, use the unoptimal queue
					currentPath = queue1_unoptimal.poll();
					unoptimal = true;
				}
	            String currentNode = currentPath.get(currentPath.size() - 1);	// Get the current node

	            // 6.2.1) Check if it's an open node
	            if (getOpenNodes().contains(currentNode)) {
	            	if (claimedNodes.contains(currentNode)) {	 // the open node was claimed by the other agent, keep it as backup
	            		path1_backup = new ArrayList<>(currentPath);
	            		path1_backup.remove(0); // Remove starting position
	            	} else {
		                path1 = new ArrayList<>(currentPath);
		                path1.remove(0); // Remove starting position
		                claimedNodes.add(currentNode); // Claim this open node
		                continue;
	            	}
	            }

	            // 6.2.2) Expand neighbors
	            for (String neighbor : getNeighborNodes(currentNode).stream().map(Node::getId).collect(Collectors.toList())) {
	                if (!visited1.contains(neighbor) && !obstacles.contains(neighbor)) { // Ignore obstacles and already visited nodes
	                    visited1.add(neighbor);
	                    List<String> newPath = new ArrayList<>(currentPath);
	                    newPath.add(neighbor);

	                    // check whether the neighbor is the other agent's position
	                    if (!sameStart && neighbor.equals(otherAgentPosition)) {
	                    	unoptimal = true;
	                    }
	                    
	                    // add the new path to the corresponding queue
						if (unoptimal) {
							queue1_unoptimal.add(newPath);
						} else {
							queue1.add(newPath);
						}
	                }
	            }
	        }

	        // 6.3) Agent 2 Search Step
	        if (!done2) {
	        	boolean unoptimal = false;
	            List<String> currentPath = queue2.poll();	// Get the current shortest path
				if (currentPath == null) { // if the queue is empty, use the unoptimal queue
					currentPath = queue2_unoptimal.poll();
					unoptimal = true;
				}
	            String currentNode = currentPath.get(currentPath.size() - 1);	// Get the current node

	            // 6.3.1) Check if it's an open node
	            if (getOpenNodes().contains(currentNode)) {
	            	if (claimedNodes.contains(currentNode)) {	 // the open node was claimed by the other agent, keep it as backup
						path2_backup = new ArrayList<>(currentPath);
						path2_backup.remove(0); // Remove starting position
					} else {
						path2 = new ArrayList<>(currentPath);
						path2.remove(0); // Remove starting position
						claimedNodes.add(currentNode); // Claim this open node
						continue;
	            	}
	            }

	            // 6.3.2) Expand neighbors
	            for (String neighbor : getNeighborNodes(currentNode).stream().map(Node::getId).collect(Collectors.toList())) {
	                if (!visited2.contains(neighbor) && !obstacles.contains(neighbor)) { // Ignore obstacles and already visited nodes
	                    visited2.add(neighbor);
	                    List<String> newPath = new ArrayList<>(currentPath);
	                    newPath.add(neighbor);

	                    // check whether the neighbor is the other agent's position
	                    if (!sameStart && neighbor.equals(myPosition)) {
	                    	unoptimal = true;
	                    }
	                    
	                    // add the new path to the corresponding queue
						if (unoptimal) {
							queue2_unoptimal.add(newPath);
						} else {
							queue2.add(newPath);
						}
	                }
	            }
	        }
	    }

	    // 7) check if both agents have found a path
		if (path1 == null) {
			path1 = (path1_backup != null) ? new ArrayList<>(path1_backup) : null;
		}
		if (path2 == null) {
			path2 = (path2_backup != null) ? new ArrayList<>(path2_backup) : null;
		}
		// the paths are null if no open nodes are accessible
	    
	    // 8) Return results in a map
	    HashMap<String, List<String>> result = new HashMap<>();
	    result.put("agent1", path1);
	    result.put("agent2", path2);
	    return result;
	}
	    

	/*
	 * Returns the shortest path to a given node given a set of obstacles
	 * Uses Breadth First Search to find the shortest path
	 */
	public List<String> getShortestPathToNodeWithObstacles(String myPosition, String targetNode, List<String> obstacles) {
		// 1) Initialize BFS
		Queue<List<String>> queue = new LinkedList<>(); // Queue for storing paths
		Set<String> visited = new HashSet<>(); // Set for storing visited nodes

		// 2) Initialize BFS with the starting position
		List<String> startPath = new ArrayList<>();
		startPath.add(myPosition);
		queue.add(startPath);
		visited.add(myPosition);

		// 3) Run BFS
		while (!queue.isEmpty()) {
			// 4) Get the current shortest path
			List<String> path = queue.poll();
			String currentNode = path.get(path.size() - 1);

			// 5) Check if the target node is reached
			if (currentNode.equals(targetNode)) {
				path.remove(0); // Remove starting position
				return path; // Return shortest path to target node
			}

			// 6) Expand neighbors
			List<String> neighbors = getNeighborNodes(currentNode).stream().map(Node::getId)
					.collect(Collectors.toList());
			for (String neighbor : neighbors) {

				// Ignore obstacles and already visited nodes
				if (!visited.contains(neighbor) && !obstacles.contains(neighbor)) {
					visited.add(neighbor);

					// Create a new path and add the neighbor
					List<String> newPath = new ArrayList<>(path);
					newPath.add(neighbor);
					queue.add(newPath);
				}
			}
		}

		// Target node was not reached
		return null;
	}
	
	
	/*
	 * Returns the shortest path to a node not included in a list of nodes (targetNodes), a set of obstacles is also given
	 * Uses Breadth First Search to find the first node not in the list
	 */
	public List<String> getShortestPathToNodeNotInListWithObstacles(String myPosition, List<String> targetNodes, List<String> obstacles) {
		// 0) keep track of the type of dead ends
		HashMap<String, String> deadEndTypes = new HashMap<>();
		
		// 1) Initialize BFS
		Queue<List<String>> queue = new LinkedList<>(); // Queue for storing paths
		Set<String> visited = new HashSet<>(); // Set for storing visited nodes

		// 2) Initialize BFS with the starting position
		List<String> startPath = new ArrayList<>();
		startPath.add(myPosition);
		queue.add(startPath);
		visited.add(myPosition);

		// 3) Run BFS
		while (!queue.isEmpty()) {
			// 4) Get the current shortest path
			List<String> path = queue.poll();
			String currentNode = path.get(path.size() - 1);

			// 5) Check if the current node is not in the target nodes
			if (!targetNodes.contains(currentNode)) {
				try {
					path.remove(0); // Remove starting position
					return path; // Return shortest path to target node
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("Error removing starting position from path: " + e.getMessage());
					System.err.println("Path: " + path);
					System.err.println("Current node: " + currentNode);
					System.err.println("Visited nodes: " + visited);
					System.err.println("Queue: " + queue);
					System.err.println("My position: " + myPosition);
					System.err.println("Target nodes: " + targetNodes);
					System.err.println("Obstacles: " + obstacles);
				}
			}

			// 6) Expand neighbors
			List<String> neighbors = getNeighborNodes(currentNode).stream().map(Node::getId)
					.collect(Collectors.toList());
			// randomize the order of neighbors to avoid complex deadlocks
			Collections.shuffle(neighbors);
			for (String neighbor : neighbors) {

				// Ignore obstacles and already visited nodes
				if (!visited.contains(neighbor) && !obstacles.contains(neighbor)) {
					visited.add(neighbor);

					// Create a new path and add the neighbor
					List<String> newPath = new ArrayList<>(path);
					newPath.add(neighbor);
					queue.add(newPath);
				}
			}
		}

		// no suitable node was found
		return null;
	}
	
	/*
	 * Returns the shortest path to a node not included in a list of nodes (targetNodes), a set of obstacles is also given
	 * Uses Breadth First Search to find the first node not in the list
	 * Also returns the type of dead end encountered
	 * - "silo dead end" if a silo was encountered
	 * - "golem dead end" if a golem and no silo was encountered
	 * - "dead end" if no golem and no silo were encountered
	 */
	public Map<String, Object> getShortestPathToNodeNotInListWithObstacles(
	        String myPosition,
	        List<String> targetNodes,
	        List<String> obstacles,
	        Map<String, String> obstacleTypes
	) {
	    // Result map
	    Map<String, Object> result = new HashMap<>();

	    // Tracking which types of obstacles we encountered during the search
	    boolean encounteredSilo = false;
	    boolean encounteredGolem = false;

	    // 1) Initialize BFS
	    Queue<List<String>> queue = new LinkedList<>();
	    Set<String> visited = new HashSet<>();

	    List<String> startPath = new ArrayList<>();
	    startPath.add(myPosition);
	    queue.add(startPath);
	    visited.add(myPosition);

	    // 2) Run BFS
	    while (!queue.isEmpty()) {
	        List<String> path = queue.poll();
	        String currentNode = path.get(path.size() - 1);

	        // Check if the node is acceptable
	        if (!targetNodes.contains(currentNode)) {
	            path.remove(0); // remove starting position
	            result.put("success", true);
	            result.put("newPath", path);
	            result.put("deadEndType", null);
	            return result;
	        }

	        // Explore neighbors
	        List<String> neighbors = getNeighborNodes(currentNode).stream()
	                .map(Node::getId)
	                .collect(Collectors.toList());
	        Collections.shuffle(neighbors); // randomize to avoid deadlocks

	        for (String neighbor : neighbors) {
	            if (!visited.contains(neighbor)) {
	                visited.add(neighbor);

	                if (obstacles.contains(neighbor)) {
	                    // Log obstacle types encountered
	                    String type = obstacleTypes.get(neighbor);
	                    if ("silo".equals(type)) encounteredSilo = true;
	                    else if ("golem".equals(type)) encounteredGolem = true;
	                    // don't add to queue
	                    continue;
	                }

	                // Add the neighbor to the path and enqueue
	                List<String> newPath = new ArrayList<>(path);
	                newPath.add(neighbor);
	                queue.add(newPath);
	            }
	        }
	    }

	    // No path found — determine dead end type
	    result.put("success", false);
	    result.put("newPath", null);

	    if (encounteredSilo) {
	        result.put("deadEndType", "siloDeadEnd");
	    } else if (encounteredGolem) {
	        result.put("deadEndType", "golemDeadEnd");
	    } else {
	        result.put("deadEndType", "deadEnd");
	    }

	    return result;
	}

	
	/*
	 * Returns the shortest path to a node 1 further than the 1st intersection found, a set of obstacles is also given
	 * Uses Breadth First Search to find the 1st intersection, then picks a neighbor of the intersection
	 */
	public List<String> getShortestPathToNodeOneFurtherThanIntersectionWithObstacle(String myPosition, List<String> obstacles) {
		// 1) Initialize BFS
		Queue<List<String>> queue = new LinkedList<>(); // Queue for storing paths
		Set<String> visited = new HashSet<>(); // Set for storing visited nodes

		// 2) Initialize BFS with the starting position
		List<String> startPath = new ArrayList<>();
		startPath.add(myPosition);
		queue.add(startPath);
		visited.add(myPosition);

		// 3) Run BFS
		while (!queue.isEmpty()) {
			// 4) Get the current shortest path
			List<String> path = queue.poll();
			String currentNode = path.get(path.size() - 1);

			// 5) Check if the current node is an intersection
			if (getNeighborNodes(currentNode).size() > 2) {
				// 6) Make sure the intersection has at least 3 neighbors that are not obstacles
				List<String> neighbors = getNeighborNodes(currentNode).stream().map(Node::getId)
						.collect(Collectors.toList());
				int count = 0;
				for (String neighbor : neighbors) {
					if (!obstacles.contains(neighbor)) {
						count++;
					}
				}
				
				
				if (count > 2) {
					// 7) The intersection has at least 3 neighbors available neighbors, return the path to the 1st neighbor that is not an obstacle or the one we come from
					// randomize the order of neighbors to avoid complex deadlocks
					Collections.shuffle(neighbors);
					for (String neighbor : neighbors) {
						// Ignore obstacles and already visited nodes
						if (!visited.contains(neighbor) && !obstacles.contains(neighbor)) {
							path.add(neighbor); // Add the neighbor
							path.remove(0); // Remove starting position
							return path; // Return shortest path to target node
						}
					}
				}
				
			}

			// 8) Expand neighbors
			List<String> neighbors = getNeighborNodes(currentNode).stream().map(Node::getId)
					.collect(Collectors.toList());
			// randomize the order of neighbors to avoid complex deadlocks
			Collections.shuffle(neighbors);
			for (String neighbor : neighbors) {

				// Ignore obstacles and already visited nodes
				if (!visited.contains(neighbor) && !obstacles.contains(neighbor)) {
					visited.add(neighbor);

					// Create a new path and add the neighbor
					List<String> newPath = new ArrayList<>(path);
					newPath.add(neighbor);
					queue.add(newPath);
				}
			}
		}

		// no suitable node was found
		return null;
	}
	
	
	public List<String> getOpenNodes(){
		return this.g.nodes()
				.filter(x ->x .getAttribute("ui.class")==MapAttribute.open.toString()) 
				.map(Node::getId)
				.collect(Collectors.toList());
	}


	/**
	 * Before the migration we kill all non serializable components and store their data in a serializable form
	 */
	public void prepareMigration(){
		serializeGraphTopology();

		closeGui();

		this.g=null;
	}

	/**
	 * Before sending the agent knowledge of the map it should be serialized.
	 */
	private void serializeGraphTopology() {
		this.sg= new SerializableSimpleGraph<String,MapAttribute>();
		Iterator<Node> iter=this.g.iterator();
		while(iter.hasNext()){
			Node n=iter.next();
			sg.addNode(n.getId(),MapAttribute.valueOf((String)n.getAttribute("ui.class")));
		}
		Iterator<Edge> iterE=this.g.edges().iterator();
		while (iterE.hasNext()){
			Edge e=iterE.next();
			Node sn=e.getSourceNode();
			Node tn=e.getTargetNode();
			sg.addEdge(e.getId(), sn.getId(), tn.getId());
		}	
	}


	public synchronized SerializableSimpleGraph<String,MapAttribute> getSerializableGraph(){
		serializeGraphTopology();
		return this.sg;
	}

	/**
	 * After migration we load the serialized data and recreate the non serializable components (Gui,..)
	 */
	public synchronized void loadSavedData(){

		this.g= new SingleGraph("My world vision");
		this.g.setAttribute("ui.stylesheet",nodeStyle);

		openGui();

		Integer nbEd=0;
		for (SerializableNode<String, MapAttribute> n: this.sg.getAllNodes()){
			this.g.addNode(n.getNodeId()).setAttribute("ui.class", n.getNodeContent().toString());
			for(String s:this.sg.getEdges(n.getNodeId())){
				this.g.addEdge(nbEd.toString(),n.getNodeId(),s);
				nbEd++;
			}
		}
		System.out.println("Loading done");
	}

	/**
	 * Method called before migration to kill all non serializable graphStream components
	 */
	private synchronized void closeGui() {
		//once the graph is saved, clear non serializable components
		if (this.viewer!=null){
			//Platform.runLater(() -> {
			try{
				this.viewer.close();
			}catch(NullPointerException e){
				System.err.println("Bug graphstream viewer.close() work-around - https://github.com/graphstream/gs-core/issues/150");
			}
			//});
			this.viewer=null;
		}
	}

	/**
	 * Method called after a migration to reopen GUI components
	 */
	private synchronized void openGui() {
		this.viewer =new FxViewer(this.g, FxViewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);//GRAPH_IN_GUI_THREAD)
		viewer.enableAutoLayout();
		viewer.setCloseFramePolicy(FxViewer.CloseFramePolicy.CLOSE_VIEWER);
		viewer.addDefaultView(true);

		g.display();
	}

	public void mergeMap(SerializableSimpleGraph<String, MapAttribute> sgreceived) {
		//System.out.println("You should decide what you want to save and how");
		//System.out.println("We currently blindy add the topology");

		for (SerializableNode<String, MapAttribute> n: sgreceived.getAllNodes()){
			//System.out.println(n);
			boolean alreadyIn =false;
			//1 Add the node
			Node newnode=null;
			try {
				newnode=this.g.addNode(n.getNodeId());
			}	catch(IdAlreadyInUseException e) {
				alreadyIn=true;
				//System.out.println("Already in"+n.getNodeId());
			}
			if (!alreadyIn) {
				newnode.setAttribute("ui.label", newnode.getId());
				newnode.setAttribute("ui.class", n.getNodeContent().toString());
			}else{
				newnode=this.g.getNode(n.getNodeId());
				//3 check its attribute. If it is below the one received, update it.
				if (((String) newnode.getAttribute("ui.class"))==MapAttribute.closed.toString() || n.getNodeContent().toString()==MapAttribute.closed.toString()) {
					newnode.setAttribute("ui.class",MapAttribute.closed.toString());
				}
			}
		}

		//4 now that all nodes are added, we can add edges
		for (SerializableNode<String, MapAttribute> n: sgreceived.getAllNodes()){
			for(String s:sgreceived.getEdges(n.getNodeId())){
				addEdge(n.getNodeId(),s);
			}
		}
		//System.out.println("Merge done");
	}

	/**
	 * 
	 * @return true if there exist at least one openNode on the graph 
	 */
	public boolean hasOpenNode() {
		return (this.g.nodes()
				.filter(n -> n.getAttribute("ui.class")==MapAttribute.open.toString())
				.findAny()).isPresent();
	}
	
	// clear the map
	public void clear() {
		this.g.clear();
	}
	
	public boolean isEmpty() {
		return this.g.getNodeCount() == 0;
	}
	
	/*
	 * method to compare the map with another map, returns only the new information from the other map	//not tested
	 * uses and returns a MapRepresentation
	 */
	public MapRepresentation getNewInformation(MapRepresentation otherMap) {
	    MapRepresentation newInfoMap = new MapRepresentation();

	    // Compare nodes
	    for (Node otherNode : otherMap.g) {
	        String nodeId = otherNode.getId();
	        Node thisNode = this.g.getNode(nodeId);

	        if (thisNode == null) {
	            // The node exists in the other map but not in ours -> add it
	            newInfoMap.addNode(nodeId, MapAttribute.valueOf((String) otherNode.getAttribute("ui.class")));
	        } else {
	            // The node exists in both maps, check if the other map has a more "up-to-date" state
	            String thisAttribute = (String) thisNode.getAttribute("ui.class");
	            String otherAttribute = (String) otherNode.getAttribute("ui.class");

	            if (thisAttribute.equals(MapAttribute.open.toString()) && otherAttribute.equals(MapAttribute.closed.toString())) {
	                // The node has been updated to closed in the other map -> add the closed version
	                newInfoMap.addNode(nodeId, MapAttribute.closed);
	            }
	        }
	    }

	    // Compare edges
	    for (Edge otherEdge : otherMap.g.edges().collect(Collectors.toList())) {
	        String node1 = otherEdge.getNode0().getId();
	        String node2 = otherEdge.getNode1().getId();

	        if (this.g.getEdge(otherEdge.getId()) == null) {
	            // The edge exists in the other map but not in ours -> add it
	            newInfoMap.addEdge(node1, node2);
	        }
	    }

	    return newInfoMap;
	}


	/*
	 * method to compare the map with another map, returns only the new information from the other map	//does not work correctly
	 * uses and returns a SerializableSimpleGraph
	 */
	public SerializableSimpleGraph<String, MapAttribute> getNewInformation(SerializableSimpleGraph<String, MapAttribute> sgreceived) {
	    SerializableSimpleGraph<String, MapAttribute> newInfoGraph = new SerializableSimpleGraph<>();

	    // Compare nodes
	    for (SerializableNode<String, MapAttribute> otherNode : sgreceived.getAllNodes()) {
	        String nodeId = otherNode.getNodeId();
	        MapAttribute otherAttribute = otherNode.getNodeContent();
	        Node thisNode = this.g.getNode(nodeId);

	        if (thisNode == null) {
	            // The node exists in the other map but not in ours -> add it
	            newInfoGraph.addNode(nodeId, otherAttribute);
	        } else {
	            // The node exists in both maps, check if the other map has a more "up-to-date" state
	            MapAttribute thisAttribute = MapAttribute.valueOf((String) thisNode.getAttribute("ui.class"));

	            if (thisAttribute == MapAttribute.open && otherAttribute == MapAttribute.closed) {
	                // The node has been updated to closed in the other map -> add the closed version
	                newInfoGraph.addNode(nodeId, MapAttribute.closed);
	            }
	        }
	    }

//	    // Compare edges		// Commenté car ne fonctionne pas correctement je pense, j'ai du mal à tester mais peut etre que sa fonctionne aussi sans (ce qui serait assez bizarre car c'est pas censé ajouter les edges sans ce code)
//	    
//
//	    // System.out.println("sgreceived: " + sgreceived);
//
//	    // get set of nodes from the other map
//	    Set<SerializableNode<String, MapAttribute>> nodes = sgreceived.getAllNodes();
//	    // System.out.println("Nodes: " + nodes);
//
//	    for (SerializableNode<String, MapAttribute> otherNode : nodes) {
//	    	// System.out.println("otherNode: " + otherNode.getNodeId());
//	    
//	    	// get set of edges connected to the current node
//	    	Set<String> edges = sgreceived.getEdges(otherNode.getNodeId());
//	    	// System.out.println("Edges: " + edges);
//	    	
//	    	// iterate over the edges
//			for (String otherNode2Id : edges) {
//				// get the other node
//				SerializableNode<String, MapAttribute> otherNode2 = nodes.stream().filter(n -> n.getNodeId().equals(otherNode2Id)).findFirst().orElse(null);
//				// System.out.println("otherNode2: " + otherNode2);
//				// test if the edge exists in our map
//				String edgeId = otherNode.getNodeId() + "-" + otherNode2Id;
//				// System.out.println("edgeId: " + edgeId);
//				
//				// Ensure the edge does not already exist in our map before adding
//				if (this.g.getEdge(edgeId) == null
//						&& this.g.getEdge(otherNode2Id + "-" + otherNode.getNodeId()) == null) {
//					// System.out.println("Adding edge: " + edgeId);
//					// Ensure both nodes exist in newInfoGraph before adding the edge
//					if (newInfoGraph.getNode(otherNode.getNodeId()) == null) {
//						newInfoGraph.addNode(otherNode.getNodeId(), otherNode.getNodeContent());
//					}
//					if (newInfoGraph.getNode(otherNode2Id) == null) {
//						newInfoGraph.addNode(otherNode2Id, otherNode2.getNodeContent());
//					}
//					// Now, safely add the edge
//					newInfoGraph.addEdge(edgeId, otherNode.getNodeId(), otherNode2Id);
//				}
//				// else {
//				// 	System.out.println("Edge already exists: " + edgeId);
//				// }
//			}
//	    }
	    
	    return newInfoGraph;
	}
	
	


	// remove nodes from the map
    public void remove(MapRepresentation map) {
    	// iterate over the nodes and edges of the map
        for (Node n : map.g) {
        	if (n != null) {
        		// remove the node from the map if it exists
        		if (this.g.getNode(n.getId()) != null) {
        			this.g.removeNode(n.getId());
        		}
        	}
			else {
				System.out.println("THIS SHOULD NEVER HAPPEN : Node in MapRepresentation is null");
			}
        }
        for (Edge e : map.g.edges().collect(Collectors.toList())) {
        	if (e != null) {
        		// remove the edge from the map if it exists
        		if (this.g.getEdge(e.getId()) != null) {
        			this.g.removeEdge(e.getId());
        		}
        	}
        	else {
        		System.out.println("THIS SHOULD NEVER HAPPEN : Edge in MapRepresentation is null");
            }
        }
    }
    
	// remove nodes from the map
    // this version should prevent having edges without nodes or things like that
    // it clears the map first, and then adds back all the edges that are in the other map (this re creates the nodes), if there were any separated nodes (not attached to any edge) they will then be added back on the 2nd for loop
    public void removeV2(MapRepresentation map) {
    	// System.out.println("Removing nodes from map");
    	// System.out.println("this.g.nodes().collect(Collectors.toList()): " + this.g.nodes().collect(Collectors.toList()));
    	// System.out.println("this.g.edges().collect(Collectors.toList()): " + this.g.edges().collect(Collectors.toList()));
    	// System.out.println("map.g.nodes().collect(Collectors.toList()): " + map.g.nodes().collect(Collectors.toList()));
    	// System.out.println("map.g.edges().collect(Collectors.toList()): " + map.g.edges().collect(Collectors.toList()));
    	
    	

    	
    	// create a copy of self
    	MapRepresentation copy = this.copy();
    	// clear self
    	this.clear();
    	
    	// System.out.println("this.g.nodes().collect(Collectors.toList()): " + this.g.nodes().collect(Collectors.toList()));
    	// System.out.println("this.g.edges().collect(Collectors.toList()): " + this.g.edges().collect(Collectors.toList()));

    	// iterate over the edges of the copy
    	List<Edge> edges = copy.g.edges().collect(Collectors.toList());
    	// System.out.println("edges: " + edges);
		for (Edge e : edges) {
			// if the edge is not in the map, add it back, along with its nodes
			// System.out.println("e: " + e);
			if (map.g.getEdge(e.getId()) == null) {
				Node node0 = e.getNode0();
				Node node1 = e.getNode1();
				// System.out.println("node0: " + node0);
				// System.out.println("node1: " + node1);
				if (this.g.getNode(node0.getId()) == null) {		// does this handle close/open status?
					this.addNode(node0.getId(), MapAttribute.valueOf((String) node0.getAttribute("ui.class")));
				}
				if (this.g.getNode(node1.getId()) == null) {		// does this handle close/open status?
					this.addNode(node1.getId(), MapAttribute.valueOf((String) node1.getAttribute("ui.class")));
				}
				this.g.addEdge(e.getId(), node0.getId(), node1.getId());
			}
			
	    	// System.out.println("this.g.nodes().collect(Collectors.toList()): " + this.g.nodes().collect(Collectors.toList()));
	    	// System.out.println("this.g.edges().collect(Collectors.toList()): " + this.g.edges().collect(Collectors.toList()));

		}
		
		// iterate over the nodes of the copy	// this is to add back the nodes that were not attached to any edge (code could be removed I think)
		for (Node n : copy.g) {
			// if the node is not in the map, add it back
			if (map.g.getNode(n.getId()) == null) {
				if (this.g.getNode(n.getId()) == null) {	// node might have been added already when adding the edges
					this.addNode(n.getId(), MapAttribute.valueOf((String) n.getAttribute("ui.class")));
				}
			}
		}
    	// System.out.println("this.g.nodes().collect(Collectors.toList()): " + this.g.nodes().collect(Collectors.toList()));
    	// System.out.println("this.g.edges().collect(Collectors.toList()): " + this.g.edges().collect(Collectors.toList()));

	}
    
    // modifies the map1 to be the same as map2
    public void modifyInputToMatchThis(MapRepresentation inputMap) {
        // Clear the input map
        inputMap.g.clear();

        // Copy nodes from this map
        for (Node nodeThis : this.g) {
            inputMap.addNode(nodeThis.getId(), MapAttribute.valueOf((String) nodeThis.getAttribute("ui.class")));
        }

        // Copy edges from this map
        for (Edge edgeThis : this.g.edges().collect(Collectors.toList())) {
            inputMap.addEdge(edgeThis.getSourceNode().getId(), edgeThis.getTargetNode().getId());
        }
    }
    
    /*
     * returns a copy of this map
     * The copy does not have a GUI
     */
	public MapRepresentation copy() {
		MapRepresentation copy = new MapRepresentation(false);

		// Copy nodes
		for (Node node : this.g) {
			copy.addNode(node.getId(), MapAttribute.valueOf((String) node.getAttribute("ui.class")));
		}

		// Copy edges
		for (Edge edge : this.g.edges().collect(Collectors.toList())) {
			copy.addEdge(edge.getNode0().getId(), edge.getNode1().getId());
		}

		return copy;
	}
	
	/*
	 * Returns the difference between our map and another,
	 * the difference is the information that is in our map but not in the other map (or that is different)
	 * It assumes that the other map is less up-to-date than our map (ie : there should not be any nodes in the other map that are not in our map)
	 */
	public void difference(MapRepresentation otherMap, MapRepresentation resultMap) {
		
		// iterate over the nodes of the our map
		for (Node node : this.g) {
			// get the node from the other map
            Node otherNode = otherMap.g.getNode(node.getId());
            
            // if the node is not in the other map, add it to the difference map
            if (otherNode == null) {
                resultMap.addNode(node.getId(), MapAttribute.valueOf((String) node.getAttribute("ui.class")));
            }
            else {
                // if the node is in the other map, check if the attributes are different, if they are, add it to the difference map
                if (!node.getAttribute("ui.class").equals(otherNode.getAttribute("ui.class"))) {
                    resultMap.addNode(node.getId(), MapAttribute.valueOf((String) node.getAttribute("ui.class")));
                }
            }
		}
		
		// iterate over the edges of our map
		for (Edge edge : this.g.edges().collect(Collectors.toList())) {
			// if the nodes of the edge are in the difference map, add the edge to the difference map
			if (edge == null) {
				System.out.println("BUG in difference method : Edge is null");	// this should never happen
			}
			else {
				Node node0 = edge.getNode0();
				Node node1 = edge.getNode1();
				if (node0 == null || node1 == null) {
					System.out.println("BUG in difference method : Node is null"); // this should never happen
				}
				else {
					if (resultMap.g.getNode(node0.getId()) != null && resultMap.g.getNode(node1.getId()) != null) {
						resultMap.addEdge(node0.getId(), node1.getId());
					}
				}
			}
		}
		
//		return resultMap;
	}
	
	public boolean equals(MapRepresentation otherMap) {
		// iterate over the nodes of the map
		for (Node node : this.g) {
			// get the node from the other map
			Node otherNode = otherMap.g.getNode(node.getId());

			// if the node is not in the other map, return false
			if (otherNode == null) {
				return false;
			}
			// if the node is in the other map, check if the attributes are different, if
			// they are, return false
			if (!node.getAttribute("ui.class").equals(otherNode.getAttribute("ui.class"))) {
				return false;
			}
		}
		
		// iterate over the edges of the other map
		for (Node node : otherMap.g) {
            // get the node from the other map
            Node otherNode = this.g.getNode(node.getId());

            // if the node is not in the other map, return false
            if (otherNode == null) {
                return false;
            }
            // if the node is in the other map, check if the attributes are different, if
            // they are, return false
            if (!node.getAttribute("ui.class").equals(otherNode.getAttribute("ui.class"))) {
                return false;
            }
        }

		// iterate over the edges of the map
		for (Edge edge : this.g.edges().collect(Collectors.toList())) {
			// if the edge is not in the other map, return false
			if (otherMap.g.getEdge(edge.getId()) == null) {
				return false;
			}
		}
		
		// iterate over the edges of the other map
		for (Edge edge : otherMap.g.edges().collect(Collectors.toList())) {
			// if the edge is not in the other map, return false
			if (this.g.getEdge(edge.getId()) == null) {
				return false;
			}
		}

		return true;
	}

	// returns a random node from the map
	public Node getRandomNode() {
		int randomIndex = new Random().nextInt(this.g.getNodeCount());
		return (Node) this.g.nodes().toArray()[randomIndex];
    }
	
	// print the map
	public void printMap() {
		System.out.println("MapRepresentation:");
		for (Node node : this.g) {
			System.out.println(node.getId() + " : " + node.getAttribute("ui.class"));
		}
		for (Edge edge : this.g.edges().collect(Collectors.toList())) {
			System.out.println(
					edge.getId() + " : " + edge.getSourceNode().getId() + " -> " + edge.getTargetNode().getId());
		}
		System.out.println();
	}
	
	// get list of nodes in the map
	public List<String> getNodes() {
		return this.g.nodes().map(Node::getId).collect(Collectors.toList());
	}
	
	// get the sum of the distances between 1 node and every other node
	public int getDistanceToAllNodes(String nodeId) {
		// BFS to get distance from nodeId to all other nodes
		Queue<List<String>> queue = new LinkedList<>();
		Set<String> visited = new HashSet<>();
		
		List<String> startPath = new ArrayList<>();
		startPath.add(nodeId);
		queue.add(startPath);
		visited.add(nodeId);
		
		int totalDistance = 0;

		while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String currentNode = path.get(path.size() - 1);
            int distance = path.size() - 1;
            totalDistance += distance;

            for (String neighbor : getNeighborNodes(currentNode).stream().map(Node::getId).collect(Collectors.toList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }
		return totalDistance;
	}
	
	// get the sum of the distances between 1 node and every other node given in a list
	public int getDistanceToAllNodes(String nodeId, List<String> nodes) {
		// BFS to get distance from nodeId to all other nodes
		Queue<List<String>> queue = new LinkedList<>();
		Set<String> visited = new HashSet<>();

		List<String> startPath = new ArrayList<>();
		startPath.add(nodeId);
		queue.add(startPath);
		visited.add(nodeId);

		int totalDistance = 0;

		while (!queue.isEmpty()) {
			List<String> path = queue.poll();
			String currentNode = path.get(path.size() - 1);
			int distance = path.size() - 1;
			if (nodes.contains(currentNode)) {
				totalDistance += distance;
			}

			for (String neighbor : getNeighborNodes(currentNode).stream().map(Node::getId)
					.collect(Collectors.toList())) {
				if (!visited.contains(neighbor)) {
					visited.add(neighbor);
					List<String> newPath = new ArrayList<>(path);
					newPath.add(neighbor);
					queue.add(newPath);
				}
			}
		}
		return totalDistance;
	}
	
	// get closest node from a list of nodes, with a set of obstacles
	public HashMap<String, Object> getClosestNodeFromList(String myPosition, List<String> nodes, List<String> obstacles) {
		HashMap<String, Object> result = new HashMap<>();

		String closestNode = null;
		int minDistance = Integer.MAX_VALUE;
		List<String> bestPath = null;

		for (String node : nodes) {
			if (!obstacles.contains(node)) {
				List<String> path = getShortestPathToNodeWithObstacles(myPosition, node, obstacles);
				if (path != null && path.size() < minDistance) {
					minDistance = path.size();
					closestNode = node;
					bestPath = path;
				}
			}
		}

		result.put("closestNode", closestNode);
		result.put("distance", minDistance);
		result.put("path", bestPath);
		return result;
	}
	
	// get the N closest nodes to a node (the node is included in the list)
//	public List<String> getClosestNodes(String myPosition, int N) {
//		if (N <= 0) {
//			return new ArrayList<>();
//		}
//		List<String> nodes = new ArrayList<>();
//		// BFS and add the neighbors to the list (random order)
//		Queue<List<String>> queue = new LinkedList<>();
//		Set<String> visited = new HashSet<>();
//		List<String> startPath = new ArrayList<>();
//		startPath.add(myPosition);
//		queue.add(startPath);
//		visited.add(myPosition);
//		
//		while (!queue.isEmpty() && nodes.size() < N) {
//			List<String> path = queue.poll();
//			String currentNode = path.get(path.size() - 1);
//			nodes.add(currentNode);
//
//			List<String> neighbors = getNeighborNodes(currentNode).stream().map(Node::getId)
//					.collect(Collectors.toList());
//			Collections.shuffle(neighbors);
//			for (String neighbor : neighbors) {
//				if (!visited.contains(neighbor)) {
//					visited.add(neighbor);
//					List<String> newPath = new ArrayList<>(path);
//					newPath.add(neighbor);
//					queue.add(newPath);
//				}
//			}
//		}
//        return nodes.stream().distinct().limit(N).collect(Collectors.toList());
//	}
	
	// get the N closest nodes to a node (the node is included in the list)
	public List<String> getClosestNodes(String myPosition, int N, boolean test) {
		if (test) {
			List<String> node = new ArrayList<>();
			node.add(myPosition); // Add the starting position to the list
			return node;
		}
		
	    if (N <= 0) {
	        return new ArrayList<>();
	    }

	    List<String> nodes = new ArrayList<>();
	    Queue<String> queue = new LinkedList<>();
	    Set<String> visited = new HashSet<>();

	    queue.offer(myPosition);
	    visited.add(myPosition);

	    while (!queue.isEmpty() && nodes.size() < N) {
	        String currentNode = queue.poll();
	        nodes.add(currentNode);

	        List<String> neighbors = getNeighborNodes(currentNode).stream()
	                .map(Node::getId)
	                .collect(Collectors.toList());
	        Collections.shuffle(neighbors); // Optional

	        for (String neighbor : neighbors) {
	            if (!visited.contains(neighbor)) {
	                visited.add(neighbor);
	                queue.offer(neighbor);
	            }
	        }
	    }

	    return nodes;
	}

}