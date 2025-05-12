package eu.su.mas.dedaleEtu.mas.behaviours.newBehaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.GsLocation;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.knowledge.TreasureInfo;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;


/**
 * Handle the centralized exploration strategy
 */
public class SiloStrategyBehaviour extends SimpleBehaviour {

	private static final long serialVersionUID = 8567689731496787661L;

	private boolean testGetClosestNodes = true;
	
	private boolean verbose = false;
	private boolean verboseProgress = true;
	
	private boolean finished = false;
	
	private boolean setupFlag = false;	// setup is false before the first action(), true after the first action()

	private long start = 0;	//used to avoid announcing the end too early

	private List<String> list_agentNames;
	
	private int stopExploringFactor = 1;
	
	ExploCoopBehaviour baseBehaviour;
	
    /*
     * all the current tasks, sorted by importance
     * task types :
     * 	- collect
     * 	- collectWithLosses
     * 	- unlock
     * 	- unlockAndCollect
     * 	- discoverOpenNode
     * 	- explore
     */
	private List<Map<String, Object>> sortedTasks;
	
	private long timeBetweenTasksReset = 15000; // 7 seconds between each task reset
	
	// the portion of tasks to look for, out of 5
	// so 1 means we will look only through the best 20% tasks
	// 2 means we will look through the best 40% tasks
	// and so on to 5 which means we will look through all the tasks
	// is incremented every fifth of timeBetweenTasksReset
	private int tasksToConsiderPortion = 5;	// starts at the maximum so it will trigger a reset of the tasks at the first call of the ticker
	
	private HashMap<String, Object> agentStatuses;
	/*
	 * "name" : {
	 *      * 	"availability" : "onMission" | "available" | "shouldBeComingBackSoon" | "lost"
	 *      * 	"shouldBeBackAt" : timeStamp
	 *      * 	"ressourceType" : "gold" | "diamond" | "None"
	 *      * 	"strengthExpertise" : int 
	 *      * 	"lockPickingExpertise" : int
	 *      * 	"backPackCapacityGold" : int
	 *      * 	"backPackCapacityDiamond" : int
	 *      }
	 * 
	 */
	
	private HashMap<String, Object> treasureStatuses;
	/*
	 * "position" : {
	 * 		* 	"amount" : int
	 *      * 	"type" : "gold" | "diamond"
	 *      * 	"strengthRequirements" : int
	 *      * 	"lockPickingRequirements" : int
	 *      * 	"LockIsOpen" : bool
	 *      * 	"lastUpdate" : timeStamp
	 *      * 	"assignement" : "assigned" | "available"
	 *      *   "assignementEndTimeStamp" : timeStamp
	 *      *	"numberOfAttemptsSoFar" : int
	 *      * 	"lastAttemptTimeStamp" : timeStamp
	 *      }
	 */
	
	private HashMap<String, Object> openNodeStatuses;
	/*
	 * "position" : {
	 *		*	"assignement" : "assigned" | "available"
	 *		*	"numberOfAttemptsSoFar" : int
	 *      *   "assignementEndTimeStamp" : timeStamp
	 *		* 	"lastAttemptTimeStamp" : timeStamp
	 *		}
	 */
	
	private HashMap<String, Object> golemStatuses;
	/*
	 * on peut ignorer pour l'instant
	 */
	
	// team capabilities
	// infinity to suppose treasures are achievable initially
	private int maxTeamStrength = Integer.MAX_VALUE;
	private int maxTeamLockPicking = Integer.MAX_VALUE;
	private boolean teamCapabilitiesCalculated = false;
	
	
	

/**
 * @param myagent reference to the agent we are adding this behaviour to
 * @param baseBehaviour base behaviour containing most of the important atttributes and handing the movements
 * @param agentNames name of the agents
 */
	public SiloStrategyBehaviour(final Agent a, ExploCoopBehaviour baseBehaviour,List<String> agentNames, boolean verbose) {
		super(a);
		this.baseBehaviour=baseBehaviour;
		this.list_agentNames=agentNames;
		this.agentStatuses=new HashMap<String, Object>();
		this.treasureStatuses=new HashMap<String, Object>();
		this.openNodeStatuses=new HashMap<String, Object>();
		this.golemStatuses=new HashMap<String, Object>();
		this.verbose=verbose;


	}
		
	/*
	 * helper function to create a collect task
	 */
	private Map<String, Object> createCollectTask(String position, String taskType, String resourceType, float importance, int amount) {
	    Map<String, Object> task = new HashMap<>();
	    task.put("position", position);
	    task.put("taskType", taskType);
	    task.put("ressourceType", resourceType);
	    task.put("importance", importance);
	    task.put("amount", amount);
	    return task;
	}

	/*
	 * helper function to create an unlock task
	 */
	private Map<String, Object> createUnlockTask(String position, String taskType, int strength, int lockPicking, float importance) {
	    Map<String, Object> task = new HashMap<>();
	    task.put("position", position);
	    task.put("taskType", taskType);
	    task.put("strengthRequirements", strength);
	    task.put("lockPickingRequirements", lockPicking);
	    task.put("importance", importance);
	    return task;
	}

	/*
	 * helper function to create an unlock and collect task
	 */
	private Map<String, Object> createUnlockAndCollectTask(String position, String taskType, String resourceType, int strength, int lockPicking, float importance, int amount) {
	    Map<String, Object> task = createUnlockTask(position, taskType, strength, lockPicking, importance);
	    task.put("ressourceType", resourceType);
	    task.put("amount", amount);
	    return task;
	}
	
	
	/*
	 * helper function to format the timestamp for the table
	 */
	private String formatTimestampForTable(long timestamp) {
	    if (timestamp == 0) {
	        return String.format("%5s", "");
	    }
	    return String.format("%05d", (timestamp % 10_000_000L) / 100L); // last 7 digits without the last 2
	}	
	/*
	 * Helper function to print treasureStatus in a clean readable table way.
	 * Will print all treasure statuses known to the Silo agent.
	 */
	private void printTreasureStatus() {
		final String ANSI_RESET = "\u001B[0m";
		final String ANSI_BRIGHT_YELLOW = "\u001B[93m"; // gold
		final String ANSI_BRIGHT_CYAN = "\u001B[96m"; // diamond
		final String ANSI_GRAY = "\u001B[90m";
		final String ANSI_GREEN = "\u001B[92m";
		final String ANSI_RED = "\u001B[91m";
	    if (this.treasureStatuses == null || this.treasureStatuses.isEmpty()) {
             System.out.println("SiloStrategy: No treasure statuses to display.");
        return;
	    }

	    // Column Headers: Pos, Typ, Amt, Str, Lck, Lock, Assign, End TS, Try, L.Att, L.Upd
	    String headerFormat = "| %-3s | %-3s | %4s | %3s | %3s | %-4s | %-10s | %5s | %3s | %5s | %5s |%n";
	    String rowFormat    = "| %-3.3s | %s | %s | %3d | %3d | %s | %s | %s | %3d | %s | %s |%n";
	    String separator    = "+-----+-----+------+-----+-----+------+------------+-------+-----+-------+-------+%n";

	    if (this.verboseProgress) {
	        System.out.printf("%nSilo Treasure Status Overview:%n");
	        System.out.printf(separator);
	        System.out.printf(headerFormat,
	                "Pos", "Typ", "Amt", "Str", "Lck", "Lock",
	                "Assignment", "End T", "Try", "L.Att", "L.Upd");
	        System.out.printf(separator);

	        for (Map.Entry<String, Object> entry : this.treasureStatuses.entrySet()) {
	            String position = entry.getKey();
	            @SuppressWarnings("unchecked")
	            Map<String, Object> status = (Map<String, Object>) entry.getValue();

	            String positionDisplay = String.format("%-3.3s", position); // suppose node id < 3 chars


	            // Type (colored)
	            String typeOriginal = (String) status.getOrDefault("type", "");
	            String typeAbbrForDisplay; 
	            String typeColor = ANSI_RESET;

	            if ("GOLD".equalsIgnoreCase(typeOriginal)) {
	                typeAbbrForDisplay = "GOL";
	                typeColor = ANSI_BRIGHT_YELLOW;
	            } else if ("DIAMOND".equalsIgnoreCase(typeOriginal)) {
	                typeAbbrForDisplay = "DIA";
	                typeColor = ANSI_BRIGHT_CYAN;
	            } else {
	                typeAbbrForDisplay = String.format("%-3.3s", typeOriginal);
	            }
	            String coloredType = typeColor + typeAbbrForDisplay + ANSI_RESET;


	            // Amount (colored if 0)
	            int amount = ((Number) status.getOrDefault("amount", 0)).intValue();
	            String formattedAmount;
	            if (amount == 0) {
	                formattedAmount = ANSI_GRAY + String.format("%4d", amount) + ANSI_RESET;
	            } else {
	                formattedAmount = String.format("%4d", amount);
	            }

	            // Other
	            int strengthReq = ((Number) status.getOrDefault("strengthRequirements", 0)).intValue();
	            int lockpickReq = ((Number) status.getOrDefault("lockPickingRequirements", 0)).intValue();
	            
	            boolean isOpen = (boolean) status.getOrDefault("lockIsOpen", false);
	            String lockStatusStr = isOpen ? ANSI_GREEN + "Open" + ANSI_RESET : ANSI_RED + "Lock" + ANSI_RESET;

	            // assignment
	            String assignmentDBStatus = (String) status.getOrDefault("assignement", "available");
	            String assignmentDisplayContent = "available";

	            if ("assigned".equals(assignmentDBStatus)) {
	                @SuppressWarnings("unchecked")
	                List<String> assignedAgents = (List<String>) status.get("assignedTo");
	                if (assignedAgents != null && !assignedAgents.isEmpty()) {
	                    assignmentDisplayContent = String.join(",", assignedAgents); 
	                }
	            }
	            
	            // Truncate to fit the column (10 characters wide) and left-justify
	            String finalAssignmentDisplay = String.format("%-10.10s", assignmentDisplayContent);
	            
	            long assignmentEndTSRaw = ((Number) status.getOrDefault("assignementEndTimeStamp", 0L)).longValue();
	            int attempts = ((Number) status.getOrDefault("numberOfAttemptsSoFar", 0)).intValue();
	            long lastAttemptTSRaw = ((Number) status.getOrDefault("lastAttemptTimeStamp", 0L)).longValue();
	            long lastUpdateTSRaw = ((Number) status.getOrDefault("lastUpdate", 0L)).longValue();

	            // Format timestamps
	            String assignmentEndTSDisp = formatTimestampForTable(assignmentEndTSRaw);
	            String lastAttemptTSDisp = formatTimestampForTable(lastAttemptTSRaw);
	            String lastUpdateTSDisp = formatTimestampForTable(lastUpdateTSRaw);


	            System.out.printf(rowFormat, positionDisplay, coloredType, formattedAmount, strengthReq, lockpickReq, lockStatusStr, finalAssignmentDisplay, assignmentEndTSDisp, attempts, lastAttemptTSDisp, lastUpdateTSDisp);
	        }
	        System.out.printf(separator);
	    }
	}

	/*
	 * update assignement
	 */
	private void updateAssignment() {
		// 1) treasure statuses
		Iterator<Map.Entry<String, Object>> it = treasureStatuses.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			String position = entry.getKey();
			@SuppressWarnings("unchecked")
			Map<String, Object> treasureStatus = (Map<String, Object>) entry.getValue();

			// if the task is finished
			if (treasureStatus.get("assignement").equals("assigned")) {
				long assignementEndTimeStamp = (long) treasureStatus.get("assignementEndTimeStamp");
				if (System.currentTimeMillis() > assignementEndTimeStamp) {
					treasureStatus.put("assignement", "available");
					treasureStatus.put("assignedTo", null);
					treasureStatus.put("assignementEndTimeStamp", 0);
				}
			}
		}
		if (verboseProgress) {
			printTreasureStatus();
		}
		
		// 2) open node statuses
		it = openNodeStatuses.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			@SuppressWarnings("unchecked")
			Map<String, Object> openNodeStatus = (Map<String, Object>) entry.getValue();

			// if the task is finished
			if (openNodeStatus.get("assignement").equals("assigned")) {
				long assignementEndTimeStamp = (long) openNodeStatus.get("assignementEndTimeStamp");
				if (System.currentTimeMillis() > assignementEndTimeStamp) {
					openNodeStatus.put("assignement", "available");
					openNodeStatus.put("assignedTo", null);
					openNodeStatus.put("assignementEndTimeStamp", 0);
				}
			}
		}
		
		// 3) Agents statuses
		it = agentStatuses.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			@SuppressWarnings("unchecked")
			HashMap<String, Object> agentStatus = (HashMap<String, Object>) entry.getValue();

			// if the task is finished
			if (agentStatus.get("availability").equals("onMission")) {
				long assignementEndTimeStamp = (long) agentStatus.get("shouldBeBackAt");
				if (System.currentTimeMillis() > assignementEndTimeStamp) {
					agentStatus.put("availability", "shouldBeComingBackSoon");
				}
			}
		}
	}
	
	
	
	/*
	 * Helper function to print sorted tasks in a clean readable table way.
	 */
	private void printSortedTasks(List<Map<String, Object>> tasksToPrint) {

	    final String ANSI_RESET = "\u001B[0m";
	    final String ANSI_BRIGHT_YELLOW = "\u001B[93m"; // gold
	    final String ANSI_BRIGHT_CYAN = "\u001B[96m"; // diamond
	    final String ANSI_BLUE = "\u001B[94m"; // For explore/discover
	    final String ANSI_MAGENTA = "\u001B[95m"; // For unlock
	    final String ANSI_WHITE = "\u001B[97m"; // For default/other tasks
	    final String ANSI_ORANGE = "\u001B[38;5;208m"; // For collect / unlockAndCollect


	    if (tasksToPrint == null || tasksToPrint.isEmpty()) {
	            System.out.println("SiloStrategy: No sorted tasks to display.");
	        return;
	    }

	    // Column Headers: Task Type, Imp., Pos, Res, Amt, Str, Lck
	    String headerFormat = "| %-16s | %-5s | %-3s | %-3s | %3s | %3s | %3s |%n";
	    String rowFormat    = "| %s | %-5.1f | %-3s | %s | %3s | %3s | %3s |%n";
	    String separator    = "+------------------+-------+-----+-----+-----+-----+-----+%n";

	    if (this.verbose) {
	        System.out.printf("%nSilo Sorted Tasks Overview:%n");
	        System.out.printf(separator);
	        System.out.printf(headerFormat,
	                "Task Type", "Imp.", "Pos", "Res", "Amt", "Str", "Lck");
	        System.out.printf(separator);

	        for (Map<String, Object> task : tasksToPrint) {
	            String taskType = (String) task.getOrDefault("taskType", "");
	            float importance = ((Number) task.getOrDefault("importance", 0.0f)).floatValue();
	            
	            String positionRaw = String.valueOf(task.getOrDefault("position", ""));
	            String positionDisplay = ("-1".equals(positionRaw) || "".equals(positionRaw)) ? "" : String.format("%-3.3s", positionRaw);

	            String amountStr = "";
	            String resourceTypeStr = "   ";
	            String strengthReqStr = "";
	            String lockpickReqStr = "";

	            String taskTypeColor = ANSI_WHITE;

	            switch (taskType) {
	                case "collect":
	                case "collectWithLosses":
	                case "unlockAndCollect":
	                    taskTypeColor = ANSI_ORANGE;
	                    amountStr = String.valueOf(task.getOrDefault("amount", ""));
	                    String resTypeRaw = (String) task.getOrDefault("ressourceType", "");
	                    if ("GOLD".equalsIgnoreCase(resTypeRaw)) {
	                        resourceTypeStr = ANSI_BRIGHT_YELLOW + "GOL" + ANSI_RESET;
	                    } else if ("DIAMOND".equalsIgnoreCase(resTypeRaw)) {
	                        resourceTypeStr = ANSI_BRIGHT_CYAN + "DIA" + ANSI_RESET;
	                    } else if (!resTypeRaw.isEmpty()){
	                        resourceTypeStr = String.format("%-3.3s",resTypeRaw);
	                    }
	                    
	                    if (taskType.equals("unlockAndCollect")) {
	                        strengthReqStr = String.valueOf(task.getOrDefault("strengthRequirements", ""));
	                        lockpickReqStr = String.valueOf(task.getOrDefault("lockPickingRequirements", ""));
	                    }
	                    break;
	                case "unlock":
	                    taskTypeColor = ANSI_MAGENTA;
	                    strengthReqStr = String.valueOf(task.getOrDefault("strengthRequirements", ""));
	                    lockpickReqStr = String.valueOf(task.getOrDefault("lockPickingRequirements", ""));
	                    break;
	                case "discoverOpenNode":
	                case "explore":
	                    taskTypeColor = ANSI_BLUE;
	                    break;
	                default:
	                    break;
	            }
	            
	            String coloredTaskType = taskTypeColor + String.format("%-16.16s", taskType) + ANSI_RESET;

	            String finalAmountStr = amountStr.isEmpty() ? " " : amountStr;
	            String finalStrengthReqStr = strengthReqStr.isEmpty() ? " " : strengthReqStr;
	            String finalLockpickReqStr = lockpickReqStr.isEmpty() ? " " : lockpickReqStr;


	            System.out.printf(rowFormat,
	                    coloredTaskType,
	                    importance,
	                    positionDisplay,
	                    resourceTypeStr,
	                    String.format("%3s", finalAmountStr),
	                    String.format("%3s", finalStrengthReqStr),
	                    String.format("%3s", finalLockpickReqStr)
	            );
	        }
	        System.out.printf(separator);
	    }
	}
	
	
	/*
	 * calculate the tasks
	 */
	private void calculateTasks() {
		// 0) reset the tasks
	    sortedTasks = new ArrayList<>();

	    // 1) Create tasks from treasures
	    for (Map.Entry<String, Object> entry : treasureStatuses.entrySet()) {
	        String position = entry.getKey();
	        @SuppressWarnings("unchecked")
	        Map<String, Object> treasureStatus = (Map<String, Object>) entry.getValue();
	        
	        // if assigned, skip
	        String assignment = (String) treasureStatus.get("assignement");
			if (assignment.equals("assigned")) {
				continue;
			}

	        int strengthRequirements = (int) treasureStatus.get("strengthRequirements");
	        int lockPickingRequirements = (int) treasureStatus.get("lockPickingRequirements");
	        boolean lockIsOpen = (boolean) treasureStatus.get("lockIsOpen");
	        int attempts = (int) treasureStatus.get("numberOfAttemptsSoFar");
	        int value = (int) treasureStatus.get("amount");
	        String resourceType = (String) treasureStatus.get("type");
	        float importance = value / (float) (attempts + 1);

	        if (lockIsOpen) {
	            sortedTasks.add(createCollectTask(position, "collect", resourceType, importance * 1.5f, value));
	            sortedTasks.add(createCollectTask(position, "collectWithLosses", resourceType, importance * 1.25f, value));
	        } else {
	            sortedTasks.add(createUnlockTask(position, "unlock", strengthRequirements, lockPickingRequirements, importance));
	            sortedTasks.add(createUnlockAndCollectTask(position, "unlockAndCollect", resourceType, strengthRequirements, lockPickingRequirements, importance * 2.5f, value));
	        }
	    }

	    // 2) Create tasks from open nodes
	    for (Map.Entry<String, Object> entry : openNodeStatuses.entrySet()) {
	        String position = entry.getKey();
	        @SuppressWarnings("unchecked")
	        Map<String, Object> status = (Map<String, Object>) entry.getValue();
	        
	        // if assigned, skip
	        String assignment = (String) status.get("assignement");
            if (assignment.equals("assigned")) {
                continue;
            }
	        
	        int attempts = (int) status.get("numberOfAttemptsSoFar");
	        float importance = 20f / (attempts*attempts + 1);

	        Map<String, Object> task = new HashMap<>();
	        task.put("position", position);
	        task.put("taskType", "discoverOpenNode");
	        task.put("importance", importance);
	        sortedTasks.add(task);
	    }

	    // 3) Add default explore task
	    Map<String, Object> exploreTask = new HashMap<>();
	    exploreTask.put("position", -1);
	    exploreTask.put("taskType", "explore");
	    exploreTask.put("importance", 1.01f);
	    sortedTasks.add(exploreTask);
	    // 4) Filter out unimportant tasks
	    sortedTasks.removeIf(task -> (float) task.get("importance") < 1);
	    // 5) Sort by importance descending
	    sortedTasks.sort((a, b) -> Float.compare((float) b.get("importance"), (float) a.get("importance")));
	    
	    if (this.verbose) {
//	    	System.out.println("\u001B[32m" + "Sorted Tasks: " + sortedTasks+ "\u001B[0m");
	    	printSortedTasks(sortedTasks);
	    }
	    
	    // 6) Determine if the agent group should consider itself finished
	    boolean onlyExploreTasks = sortedTasks.isEmpty() || (sortedTasks.size() == 1 && "explore".equals(sortedTasks.get(0).get("taskType")));
	    boolean assignedTaskExists = false;
	    int totalTreasureAmount = 0;
	    
	    if (onlyExploreTasks) {
            for (Map.Entry<String, Object> entry : this.openNodeStatuses.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nodeStatus = (Map<String, Object>) entry.getValue();
                if ("assigned".equals(nodeStatus.get("assignement"))) {
                    assignedTaskExists = true; // assigned exploration is ongoing
                    break;
	            }
	        }
	    }
	    boolean noAchievableTreasuresLeft = true;
	    for (Map.Entry<String, Object> entry : this.treasureStatuses.entrySet()) {
	         @SuppressWarnings("unchecked")
	         Map<String, Object> treasureStatus = (Map<String, Object>) entry.getValue();
	         int amount = ((Number) treasureStatus.get("amount")).intValue();
	         
	         // sum treasure amount
	         totalTreasureAmount += amount;
	         
	         // check if treasure achievable
	         if (amount > 0) {
                 int requiredStrength = (int) treasureStatus.get("strengthRequirements");
                 int requiredLockPicking = (int) treasureStatus.get("lockPickingRequirements");
                 if (requiredStrength <= this.maxTeamStrength && requiredLockPicking <= this.maxTeamLockPicking) {
                     noAchievableTreasuresLeft = false; // achievable
//						if (this.verboseProgress) {
//							System.out.println("\u001B[32m" + "Silo: Achievable treasure found at " + entry.getKey()
//									+ " with amount " + amount + "\u001B[0m");
//						}
                }
	        }
	    }


	    if ((onlyExploreTasks && !assignedTaskExists) || noAchievableTreasuresLeft || totalTreasureAmount == 0) {
	        if (System.currentTimeMillis() - this.start > 60000) { 
	            if (this.verboseProgress) {
	            	if (noAchievableTreasuresLeft) {
	            		System.out.println("\u001B[36m" + "\nSilo: Finished (no achievable treasures left).\n" + "\u001B[0m");
	            	} else {
	            		System.out.println("\u001B[36m" + "\nSilo: Finished.\n" + "\u001B[0m");
	            	}
	            }
	        }
	    } else {
	        if (onlyExploreTasks && this.verboseProgress) {
	             System.out.println("\u001B[96m" + "Silo: Waiting for assigned tasks/nodes to complete. Only explore task is currently available from sorted tasks." + "\u001B[0m");
	        }
	    }
	}
	
	
	private void calculateAndStoreMaxTeamCapabilities() {
	    if (this.teamCapabilitiesCalculated) {
	        return;
	    }

	    // making sure this.list_agentNames is populated
	    if (this.list_agentNames == null || this.list_agentNames.isEmpty()) {
	        if (this.verbose) System.out.println("SiloStrategy: Collaborator list (this.list_agentNames) is empty, cannot calculate team capabilities yet.");
	        return;
	    }

	    boolean allCollaboratorsReported = true;
	    for (String agentName : this.list_agentNames) {
	        if (!this.agentStatuses.containsKey(agentName)) {
	            allCollaboratorsReported = false;
	            break;
	        }
	    }

	    if (allCollaboratorsReported) {
	        int currentMaxStrength = 0;
	        int currentMaxLockPicking = 0;
	        for (String agentName : this.list_agentNames) {
	            @SuppressWarnings("unchecked")
	            HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
	            currentMaxStrength += (int) agentStatus.getOrDefault("strengthExpertise", 0);
	            currentMaxLockPicking += (int) agentStatus.getOrDefault("lockPickingExpertise", 0);
	        }
	        this.maxTeamStrength = currentMaxStrength;
	        this.maxTeamLockPicking = currentMaxLockPicking;
	        this.teamCapabilitiesCalculated = true;
	        
	        if (this.verboseProgress) {
	            System.out.println("\u001B[34m" + "SiloStrategy: Max Team Capabilities Calculated. Strength: " + this.maxTeamStrength + ", LockPicking: " + this.maxTeamLockPicking + "\u001B[0m");
	        }
	    } else {
//	        if (this.verbose) {
//	            System.out.println("SiloStrategy: Waiting for all collaborators in list_agentNames (" + this.list_agentNames.size() + " agents: " + this.list_agentNames + ") to report status before calculating team capabilities. Currently known: " + this.agentStatuses.keySet());
//	        }
	    }
	}

	
	
	
	
	
	/*
	 * called the first time action() is called
	 */
	private void setup() {
		// 1) 
		this.sortedTasks = new ArrayList<>();
		// Add an internal TickerBehaviour to periodically recreate the tasks
		this.myAgent.addBehaviour(new TickerBehaviour(this.myAgent, timeBetweenTasksReset/5) {
            @Override
            protected void onTick() {
            	// 0) calculate team capabilities once
            	if (!teamCapabilitiesCalculated) {
                    calculateAndStoreMaxTeamCapabilities();
                }
            	
            	// 1) increment the tasksToConsiderPortion
            	tasksToConsiderPortion++;
				if (tasksToConsiderPortion > 5) {
					// reset the tasksToConsiderPortion
					tasksToConsiderPortion = 1;
					updateOpenNodeStatus();
					updateTreasureStatus();
					updateAssignment();
					calculateTasks();
				}
				int safetyCounter = 10;
				while (tryAssigningTasks() && safetyCounter > 0) {
					safetyCounter--;
				}
				
            }
        });
		
		// 2) add the listening behaviour
		this.myAgent.addBehaviour(new ReceiveCommunicationWithSiloBehaviour(this.myAgent, this, this.verbose));
		
		this.start = System.currentTimeMillis();
//        // add the sharing behaviour
//        this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent, 500, this.myMap, this.list_agentNames));
        // make sure setup() is not called again
        this.setupFlag = true;
    }
	
	/*
	 * get the top (20% * tasksToConsiderPortion) tasks
	 * filters out the task types that are not in the filters
	 */
	public List<Map<String, Object>> getTopTasks(List<String> filters) {

		// 1) get the top tasks
		int numberOfTasksToConsider = tasksToConsiderPortion * sortedTasks.size() / 5;
		if (numberOfTasksToConsider == 0) {
			numberOfTasksToConsider = 1;
		}

		List<Map<String, Object>> topTasks = new ArrayList<>();
		// 2) iterate through the sorted tasks and add them to the topTasks list if they are in the filters
		// stop when we reach the numberOfTasksToConsider or the end of the list
		int count = 0;
		for (Map<String, Object> task : sortedTasks) {
			if (count >= numberOfTasksToConsider) {
				break;
			}
			if (filters.contains(task.get("taskType"))) {
				topTasks.add(task);
//				count++;
			}
			count++;
		}

		return topTasks;
	}
	
	/*
	 * Version without filters
	 * get the top (20% * tasksToConsiderPortion) tasks
	 * filters out the task types that are not in the filters
	 */
	public List<Map<String, Object>> getTopTasks() {
		List<String> filters = new ArrayList<>();
		filters.add("collect");
		filters.add("collectWithLosses");
		filters.add("unlock");
		filters.add("unlockAndCollect");
		filters.add("discoverOpenNode");
		filters.add("explore");
		return getTopTasks(filters);
	}

	
	/*
	 * get agent type
	 * -> agentCollect, agentExplo, agentTanker
	 */
	public String getAgentType(int backPackCapacityGold, int backPackCapacityDiamond) {
		if (backPackCapacityGold > 0 && backPackCapacityDiamond > 0) {
			return "agentTanker";
		} else if (backPackCapacityGold > 0 || backPackCapacityDiamond > 0) {
			return "agentCollect";
		} else {
			return "agentExplo";
		}
	}
	
	/*
	 * returns the compatible tasks available at this time for that agent type
	 */
	public List<Map<String, Object>> getCompatibleTasks(String agentName) {
		// 1) get the agent's attributes
		HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
		int strengthExpertise = (int) agentStatus.get("strengthExpertise");
		int lockPickingExpertise = (int) agentStatus.get("lockPickingExpertise");
		int backPackCapacityGold = (int) agentStatus.get("backPackCapacityGold");
		int backPackCapacityDiamond = (int) agentStatus.get("backPackCapacityDiamond");
		String agentType = getAgentType(backPackCapacityGold, backPackCapacityDiamond);
		
		// 2) get the filters for the agent type
		List<String> filters = new ArrayList<String>();
		switch (agentType) {
		case "agentCollect":
			filters.add("collect");
			filters.add("collectWithLosses");
			filters.add("unlock");
			filters.add("unlockAndCollect");
			filters.add("discoverOpenNode");
			filters.add("explore");
			break;
		case "agentExplo":
			filters.add("unlock");
			filters.add("discoverOpenNode");
			filters.add("explore");
			break;
		case "agentTanker":
			filters.add("unlock");
			filters.add("discoverOpenNode");
			filters.add("explore");
			break;
		}

		// 3) get the top tasks
		List<Map<String, Object>> compatibleTasks = getTopTasks(filters);
		return compatibleTasks;
	}
	
	/*
	 * creates a waitForSiloMessage mission
	 */
	public HashMap<String, Object> createWaitForSiloMessageMission() {
		// 1) timeout = next task reset + 1 second
		long timeOut = System.currentTimeMillis() + 1000 + (timeBetweenTasksReset - ((5 - tasksToConsiderPortion) * timeBetweenTasksReset / 5));
		
		// 2) create the mission
		HashMap<String, Object> mission = new HashMap<>();
		mission.put("missionType", "waitForSiloMessage");
		mission.put("missionDestinations", null);
		mission.put("timeOut", timeOut);
		return mission;
	}
	
	/*
	 * check if an agent can SOLO a task
	 */
	public boolean canAgentSoloTask(String agentName, Map<String, Object> task) {
		// 0) explore and discoverOpenNode tasks are always possible
		if (task.get("taskType").equals("explore") || task.get("taskType").equals("discoverOpenNode")) {
			return true;
		}
		
		// 1) get the agent's attributes
		@SuppressWarnings("unchecked")
		HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
		int strengthExpertise = (int) agentStatus.get("strengthExpertise");
		int lockPickingExpertise = (int) agentStatus.get("lockPickingExpertise");
		int backPackCapacityGold = (int) agentStatus.get("backPackCapacityGold");
		int backPackCapacityDiamond = (int) agentStatus.get("backPackCapacityDiamond");
		String resourceType = null;
		int amount = 0;
		int strengthRequirements = 0;
		int lockPickingRequirements = 0;

		String taskType = (String) task.get("taskType");
		switch (taskType) {
		case "collect":
			resourceType = (String) task.get("ressourceType");
			amount = (int) task.get("amount");
			if ("gold".equalsIgnoreCase(resourceType) && backPackCapacityGold > 0 && amount <= backPackCapacityGold) {
				return true;
			} else if ("diamond".equalsIgnoreCase(resourceType) && backPackCapacityDiamond > 0 && amount <= backPackCapacityDiamond) {
				return true;
			}
			return false;
		case "collectWithLosses":

			resourceType = (String) task.get("ressourceType");
			amount = (int) task.get("amount");
			if ("gold".equalsIgnoreCase(resourceType) && backPackCapacityGold > 0) {
				return true;
			} else if ("diamond".equalsIgnoreCase(resourceType) && backPackCapacityDiamond > 0) {
				return true;
			}
			return false;
		case "unlock":
			strengthRequirements = (int) task.get("strengthRequirements");
            lockPickingRequirements = (int) task.get("lockPickingRequirements");
            if (strengthExpertise >= strengthRequirements && lockPickingExpertise >= lockPickingRequirements) {
                return true;
            }
            return false;
		case "unlockAndCollect":
			resourceType = (String) task.get("ressourceType");
            amount = (int) task.get("amount");
            strengthRequirements = (int) task.get("strengthRequirements");
            lockPickingRequirements = (int) task.get("lockPickingRequirements");
            if (strengthExpertise >= strengthRequirements && lockPickingExpertise >= lockPickingRequirements) {
				if ("gold".equalsIgnoreCase(resourceType) && backPackCapacityGold > 0 && amount <= backPackCapacityGold) {
					return true;
				} else if ("diamond".equalsIgnoreCase(resourceType) && backPackCapacityDiamond > 0 && amount <= backPackCapacityDiamond) {
					return true;
				}
			}
            return false;
		}
		return false;
	}
	
	/*
	 * Check if a group of agents can work together to complete a task
	 */
	public boolean canAgentsWorkTogether(List<String> agents, Map<String, Object> task) {
		// 0) explore and discoverOpenNode tasks are always possible
		if (task.get("taskType").equals("explore") || task.get("taskType").equals("discoverOpenNode")) {
			return true;
		}
		
		// 1) get the agents' attributes
		List<HashMap<String, Object>> agentsStatus = new ArrayList<>();
		for (String agentName : agents) {
			@SuppressWarnings("unchecked")
			HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
			agentsStatus.add(agentStatus);
		}
		int cumulativeStrengthExpertise = 0;
		int cumulativeLockPickingExpertise = 0;
		String resourceType = null;
		int amount = 0;
		int strengthRequirements = 0;
		int lockPickingRequirements = 0;
		String taskType = (String) task.get("taskType");
		
		switch (taskType) {
		case "collect":
			resourceType = (String) task.get("ressourceType");
			amount = (int) task.get("amount");
			if (resourceType.equals("gold")) {
                for (String agentName : agents) {
					// check if the agent can collect all the gold
                	HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
                	int agentBackPackCapacityGold = (int) agentStatus.get("backPackCapacityGold");
                	if (amount > agentBackPackCapacityGold) {
                		return false;
                	}
                }
            } else if (resourceType.equals("diamond")) {
                for (String agentName : agents) {
                    // check if the agent can collect all the diamond
                    HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
                    int agentBackPackCapacityDiamond = (int) agentStatus.get("backPackCapacityDiamond");
					if (amount > agentBackPackCapacityDiamond) {
						return false;
					}
                }
			}
			return true;

		case "collectWithLosses":
			resourceType = (String) task.get("ressourceType");
			amount = (int) task.get("amount");
			if (resourceType.equals("gold")) {
				for (String agentName : agents) {
					// check if the agent can collect all the gold
					HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
					int agentBackPackCapacityGold = (int) agentStatus.get("backPackCapacityGold");
					if (0 >= agentBackPackCapacityGold) {
						return false;
					}
				}
			} else if (resourceType.equals("diamond")) {
				for (String agentName : agents) {
					// check if the agent can collect all the diamond
					HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
					int agentBackPackCapacityDiamond = (int) agentStatus.get("backPackCapacityDiamond");
					if (0 >= agentBackPackCapacityDiamond) {
						return false;
					}
				}
			}
			return true;

		case "unlock":
			strengthRequirements = (int) task.get("strengthRequirements");
			lockPickingRequirements = (int) task.get("lockPickingRequirements");
			for (HashMap<String, Object> agentStatus : agentsStatus) {
				cumulativeStrengthExpertise += (int) agentStatus.get("strengthExpertise");
				cumulativeLockPickingExpertise += (int) agentStatus.get("lockPickingExpertise");
			}
			if (cumulativeStrengthExpertise >= strengthRequirements && cumulativeLockPickingExpertise >= lockPickingRequirements) {
				return true;
			}
			return false;
			
		case "unlockAndCollect":
			resourceType = (String) task.get("ressourceType");
			amount = (int) task.get("amount");
			strengthRequirements = (int) task.get("strengthRequirements");
			lockPickingRequirements = (int) task.get("lockPickingRequirements");
			for (HashMap<String, Object> agentStatus : agentsStatus) {
				cumulativeStrengthExpertise += (int) agentStatus.get("strengthExpertise");
				cumulativeLockPickingExpertise += (int) agentStatus.get("lockPickingExpertise");
			}
			if (cumulativeStrengthExpertise >= strengthRequirements && cumulativeLockPickingExpertise >= lockPickingRequirements) {
				if (resourceType.equals("gold")) {
					for (String agentName : agents) {
						HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
						int agentBackPackCapacityGold = (int) agentStatus.get("backPackCapacityGold");
						if (amount > agentBackPackCapacityGold) {
							return false;
						}
					}
				} else if (resourceType.equals("diamond")) {
					for (String agentName : agents) {
						HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
						int agentBackPackCapacityDiamond = (int) agentStatus.get("backPackCapacityDiamond");
						if (amount > agentBackPackCapacityDiamond) {
							return false;
						}
					}
				}
				return true;
			}
			return false;
		}
		return false;
	}
	
	/*
	 * get the available agents
	 * filters out the agents that do not have a type in the filters
	 */
	public List<String> getAvailableAgents(List<String> filters) {
		// 1) get the available agents
		List<String> availableAgents = new ArrayList<>();
		for (String agentName : this.agentStatuses.keySet()) {
			HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
			String availability = (String) agentStatus.get("availability");
			if (availability.equals("available")) {
				availableAgents.add(agentName);
			}
		}

		// 2) filter out the agents that do not have a type in the filters
		List<String> filteredAvailableAgents = new ArrayList<>();
		for (String agentName : availableAgents) {
			HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
			int backPackCapacityGold = (int) agentStatus.get("backPackCapacityGold");
			int backPackCapacityDiamond = (int) agentStatus.get("backPackCapacityDiamond");
			String agentType = getAgentType(backPackCapacityGold, backPackCapacityDiamond);
			if (filters.contains(agentType)) {
				filteredAvailableAgents.add(agentName);
			}
		}

		return filteredAvailableAgents;
	}
	
	/*
	 * get all the possible combinations of agents
	 * groupSize is the number of agents in the group
	 * TODO: test if it works
	 */
	public List<List<String>> getAllCombinations(List<String> agents, int groupSize) {
		List<List<String>> result = new ArrayList<>();
		if (groupSize <= 0 || groupSize > agents.size()) {
			return result;
		}
		getAllCombinationsBacktrack(agents, groupSize, 0, new ArrayList<>(), result);
		return result;
	}

	/*
	 * helper function to get all combinations of agents
	 */
	private void getAllCombinationsBacktrack(List<String> agents, int groupSize, int start, List<String> current, List<List<String>> result) {
		if (current.size() == groupSize) {
			result.add(new ArrayList<>(current));
			return;
		}
		for (int i = start; i < agents.size(); i++) {
			current.add(agents.get(i));
			getAllCombinationsBacktrack(agents, groupSize, i + 1, current, result);
			current.remove(current.size() - 1);
		}
	}

	
	
	/*
	 * get a new mission for an agent
	 */
	public HashMap<String, Object> getNewMission(String agentName) {
		// 1) get the compatible tasks
		List<Map<String, Object>> compatibleTasks = getCompatibleTasks(agentName);
//		if (this.verbose) { // this debug might be important, keeping commented
//			System.out.println("Compatible Tasks for " + agentName + ": " + compatibleTasks);
//		}
		
		// if there are no compatible tasks, return a waitForSiloMessage mission
		// there should always be at least the "explore" task returned if I haven't messed up
		if (compatibleTasks.isEmpty()) {
			return createWaitForSiloMessageMission();
		}
		
		// 2) check if the agent can SOLO any of the tasks
		for (Map<String, Object> task : compatibleTasks) {
			if (canAgentSoloTask(agentName, task)) {
				// test if it's a explore task
				if (task.get("taskType").equals("explore")) {
					// randomly decide to not send them to explore but waitForSilo
					// odds of not exploring starts at 1/100 and increment to 90/100
					int random = (int) (Math.random() * 100);
					System.out.println("stopExploringFactor: " + stopExploringFactor);
					if (stopExploringFactor < 90) {
						stopExploringFactor = stopExploringFactor + 2;
					}
					if (random < stopExploringFactor) {
						// send them to waitForSilo
						return createWaitForSiloMessageMission();
					}
				}
				// 3) if the agent can SOLO the task, return it
				return assignTask(agentName, task);
			}
		}
		
		// 4) TODO: check if the agent can work with other agents
		// 4) Easier : return a waitForSiloMessage mission, the next time tryAssigningTasks() is called, the agent will be assigned to a group task if there is one
		return createWaitForSiloMessageMission();
	}
	
	/*
	 * get mission type names (kinda messed up and used different names between files)
	 */
	public String getMissionTypeNames(String missionType) {
		switch (missionType) {
		case "collect":
			return "pickUpTreasure";
		case "collectWithLosses":
			return "pickUpTreasure";
		case "unlock":
			return "unlockTreasure";
		case "unlockAndCollect":
			return "unlockAndPickUpTreasure";
		case "discoverOpenNode":
			return "exploreOpenNodes";
		case "explore":
			return "explore";
		case "waitForSiloMessage":
			return "waitForSiloMessage";
		default:
			return null;
		}
	}
	
	/*
	 * assign a task to a list of agents
	 * returns the mission to send to the agents
	 */
	public HashMap<String, Object> assignTask(List<String> agentNames, Map<String, Object> task) {
		
		if (this.verboseProgress) {
			System.out.println("\u001B[32m" + agentNames + "\u001B[0m has been assigned: \u001B[32m" + task + "\u001B[0m");
			
		}
		
		
		// 1) update the treasure/openNode
		String taskType = (String) task.get("taskType");
		HashMap<String, Object> mission = new HashMap<>();
		
		switch (taskType) {
		case "collect":
		case "collectWithLosses":
		case "unlock":
		case "unlockAndCollect":
			// 1) remove the task from the list of tasks
			sortedTasks.remove(task);
			
			// 2) get the required values
			String position = (String) task.get("position");
			HashMap<String, Object> treasureStatus = (HashMap<String, Object>) this.treasureStatuses.get(position);
			int distance;
			try {
				distance = this.baseBehaviour.getMyMap().getShortestPath(this.baseBehaviour.getPosition().getLocationId(), position).size();
			} catch (Exception e) {
				if (this.verbose) {
					System.out.println("Error getting shortest path: " + e.getMessage());
				}
				distance = 20;
			}
			long timeOut = System.currentTimeMillis() + (distance * 1000) + 5000;	// 0.5 second per step, + 1 second for safety
			long timeOutDouble = System.currentTimeMillis() + 2*((distance * 1000) + 5000);

			// 3) update the treasure status
			treasureStatus.put("assignement", "assigned");
			treasureStatus.put("assignedTo",  new ArrayList<>(agentNames));
			treasureStatus.put("lastAttemptTimeStamp", System.currentTimeMillis());
			treasureStatus.put("numberOfAttemptsSoFar", ((int) treasureStatus.get("numberOfAttemptsSoFar")) + 1);
			treasureStatus.put("assignementEndTimeStamp", timeOutDouble);
			
			// 4) update the agent statuses
			for (String agentName : agentNames) {
				HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
				agentStatus.put("availability", "onMission");
				agentStatus.put("shouldBeBackAt", timeOutDouble);
			}
			
			// 5) create the mission
			mission.put("missionType", getMissionTypeNames(taskType));
			List<String> missionDestinations = (List<String>) this.baseBehaviour.getMyMap().getClosestNodes(position, agentNames.size(), testGetClosestNodes);
			mission.put("missionDestinations", missionDestinations);
			mission.put("timeOut", timeOut);
			return mission;
		case "discoverOpenNode":
			// 1) remove the task from the list of tasks
			sortedTasks.remove(task);

			// 2) get the required values
			position = (String) task.get("position");
			HashMap<String, Object> openNodeStatus = (HashMap<String, Object>) this.openNodeStatuses.get(position);
			try {
				distance = this.baseBehaviour.getMyMap()
						.getShortestPath(this.baseBehaviour.getPosition().getLocationId(), position).size();
			} catch (Exception e) {
				if (this.verbose) {
					System.out.println("Error getting shortest path: " + e.getMessage());
				}
				distance = 20;
			}
			timeOut = System.currentTimeMillis() + (distance * 500) + 5000; // 0.5 second per step, + 5 second for searching more
			timeOutDouble = System.currentTimeMillis() + 2*((distance * 500) + 5000);

			// 3) update the open node status
			openNodeStatus.put("assignement", "assigned");
			openNodeStatus.put("lastAttemptTimeStamp", System.currentTimeMillis());
			openNodeStatus.put("numberOfAttemptsSoFar", ((int) openNodeStatus.get("numberOfAttemptsSoFar")) + 1);
			openNodeStatus.put("assignementEndTimeStamp", timeOutDouble);

			// 4) update the agent statuses
			for (String agentName : agentNames) {
				HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
				agentStatus.put("availability", "onMission");
				agentStatus.put("shouldBeBackAt", timeOutDouble);
			}

			// 5) create the mission
			mission.put("missionType", getMissionTypeNames(taskType));
			missionDestinations = (List<String>) this.baseBehaviour.getMyMap().getClosestNodes(position,
					agentNames.size(), testGetClosestNodes);
			mission.put("missionDestinations", missionDestinations);
			mission.put("timeOut", timeOut);

			return mission;
		case "explore":
			// 2) get the required values
			
			timeOut = System.currentTimeMillis() + 10000; // 10 seconds

			// 3) create the mission
			mission.put("missionType", getMissionTypeNames(taskType));
			mission.put("missionDestinations", null);
			mission.put("timeOut", timeOut);
			
			// 4) update the agent statuses
			for (String agentName : agentNames) {
				HashMap<String, Object> agentStatus = (HashMap<String, Object>) this.agentStatuses.get(agentName);
				agentStatus.put("availability", "onMission");
				agentStatus.put("shouldBeBackAt", timeOut);
			}

			return mission;
		//case "waitForSiloMessage": // use createWaitForSiloMessageMission() instead
		}
		return null;
	}
	
	/*
	 * assign a task to an agent
	 * returns the mission to send to the agent
	 */
	public HashMap<String, Object> assignTask(String agentName, Map<String, Object> task) {
		List<String> agent = new ArrayList<>();
		agent.add(agentName);
		return assignTask(agent, task);
	}
	
	/*
	 * send a mission to a list of agents
	 */
	public void sendMissionToAgent(List<String> agentName, HashMap<String, Object> mission) {
		if (agentName.isEmpty() || agentName == null || mission == null) {
			return;
		}
		
		for (String receiver : agentName) {
			String messageId = "MSG-" + System.currentTimeMillis() + "-" + this.myAgent.getLocalName();
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
	        msg.setProtocol("MISSION");
	        msg.setSender(this.myAgent.getAID());
	        msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
	        msg.setConversationId(messageId);
	        
	        try {
				msg.setContentObject(mission);
			} catch (IOException e) {
				e.printStackTrace();
			}
	        
	        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
	        
//	        if (this.verbose) {
//	        	System.out.println("Silo - Sending message to " + receiver + ": " + mission);
//	        }
		}
	}
	
	/*
	 * version without list
	 * sends the mission to a single agent
	 */
	public void sendMissionToAgent(String agentName, HashMap<String, Object> mission) {
		if (agentName == null) {
			return;
		}
		List<String> agent = new ArrayList<>();
		agent.add(agentName);
		sendMissionToAgent(agent, mission);
	}
	
	/*
	 * Find and try to assign tasks to the available agents
	 * returns true if a task was assigned, false otherwise
	 */
	public boolean tryAssigningTasks () {
        // 1) get the tasks
		List<Map<String, Object>> tasks = getTopTasks();
		if (tasks.isEmpty()) {
			return false;
		}
		
		// 1) get the available agents
		List<String> filters = new ArrayList<>();
		filters.add("agentCollect");
		List<String> availableAgentsCollect = getAvailableAgents(filters);
		filters.add("agentTanker");
//		List<String> availableAgentsUnlock = getAvailableAgents(filters);
		filters.add("agentExplo");
		List<String> availableAgents = getAvailableAgents(filters);
		
		// 2) iterate through all the tasks to find if one can be SOLOed
		for (Map<String, Object> task : tasks) {
			String taskType = (String) task.get("taskType");
			switch (taskType) {
			case "collect":
			case "collectWithLosses":
			case "unlockAndCollect":
				for (String agentName : availableAgentsCollect) {
					if (canAgentSoloTask(agentName, task)) {
						// assign the task to the agent
						sendMissionToAgent(agentName, assignTask(agentName, task));
						return true;
					}
				}
				break;
			case "unlock":
				for (String agentName : availableAgents) {
					if (canAgentSoloTask(agentName, task)) {
						// assign the task to the agent
						sendMissionToAgent(agentName, assignTask(agentName, task));
						return true;
					}
				}
				break;
			case "discoverOpenNode":
                for (String agentName : availableAgents) {
                    if (canAgentSoloTask(agentName, task)) {
                        // assign the task to the agent
                    	sendMissionToAgent(agentName, assignTask(agentName, task));
                    	return true;
                    }
                }
                break;
			case "explore":
				// randomly decide to not send them to explore but waitForSilo
				// odds of not exploring starts at 1/100 and increment to 90/100
				int random = (int) (Math.random() * 100);
//				System.out.println("stopExploringFactor: " + stopExploringFactor);
				if (stopExploringFactor < 90) {
					stopExploringFactor = stopExploringFactor + 2;
				}
				if (random < stopExploringFactor) {
					// send them to waitForSilo
					for (String agentName : availableAgents) {
						sendMissionToAgent(agentName, createWaitForSiloMessageMission());
					}
					return true;
				}
				for (String agentName : availableAgents) {
					if (canAgentSoloTask(agentName, task)) {
						// assign the task to the agent
						sendMissionToAgent(agentName, assignTask(agentName, task));
						return true;
					}
				}
				break;
			}
		}

		// 3) iterate through all the acceptable group sizes
		int maxGroupSize = 4;
		int groupSize = 2;
		for (int i = 2; i <= maxGroupSize; i++) {
			// 3.1) get the combinations of agents
			List<List<String>> combinationAvailableAgentsCollect = getAllCombinations(availableAgentsCollect, groupSize);
//			List<List<String>> combinationAvailableAgentsUnlock = getAllCombinations(availableAgentsUnlock, groupSize);
			List<List<String>> combinationAvailableAgents = getAllCombinations(availableAgents, groupSize);

			// 3.2) iterate through all the tasks to find if one can be done in group
			for (Map<String, Object> task : tasks) {
				String taskType = (String) task.get("taskType");
				switch (taskType) {
				case "collect":
				case "collectWithLosses":
				case "unlockAndCollect":
					for (List<String> agents : combinationAvailableAgentsCollect) {
						if (canAgentsWorkTogether(agents, task)) {
							// assign the task to the agents
							sendMissionToAgent(agents, assignTask(agents, task));
							return true;
						}
					}
					break;
				case "unlock":
					for (List<String> agents : combinationAvailableAgents) {
						if (canAgentsWorkTogether(agents, task)) {
							// assign the task to the agents
							sendMissionToAgent(agents, assignTask(agents, task));
							return true;
						}
					}
					break;
				case "discoverOpenNode":
				case "explore":
					for (List<String> agents : combinationAvailableAgents) {
						if (canAgentsWorkTogether(agents, task)) {
							// assign the task to the agents
							sendMissionToAgent(agents, assignTask(agents, task));
							return true;
						}
					}
					break;
				}
			}
			
			groupSize++;
		}
        return false;
	}
	
	
	/*
	 * add/update an agent to the list of agents
	 */
	public void addAgent(String agentName, int strengthExpertise, int lockPickingExpertise, int backPackCapacityGold, int backPackCapacityDiamond) {
		HashMap<String, Object> agentStatus = new HashMap<String, Object>();
		agentStatus.put("availability", "available");
		agentStatus.put("shouldBeBackAt", 0);
		agentStatus.put("ressourceType", null);
		agentStatus.put("strengthExpertise", strengthExpertise);
		agentStatus.put("lockPickingExpertise", lockPickingExpertise);
		agentStatus.put("backPackCapacityGold", backPackCapacityGold);
		agentStatus.put("backPackCapacityDiamond", backPackCapacityDiamond);
		this.agentStatuses.put(agentName, agentStatus);
	}
	
	/*
	 * update a treasure to the list of treasures
	 */
	public void updateTreasure(String position, int amount, String type, int strengthRequirements, int lockPickingRequirements, boolean lockIsOpen, long lastUpdate) {
		// 1) check if the treasure is not already in the list
		if (!this.treasureStatuses.containsKey(position)) {
			// add the treasure
			HashMap<String, Object> treasureStatus = new HashMap<String, Object>();
			treasureStatus.put("amount", amount);
			treasureStatus.put("type", type);
			treasureStatus.put("strengthRequirements", strengthRequirements);
			treasureStatus.put("lockPickingRequirements", lockPickingRequirements);
			treasureStatus.put("lockIsOpen", lockIsOpen);
			treasureStatus.put("lastUpdate", lastUpdate);
			treasureStatus.put("assignement", "available");
			treasureStatus.put("assignedTo", null);
			treasureStatus.put("assignementEndTimeStamp", 0);
			treasureStatus.put("numberOfAttemptsSoFar", 0);
			treasureStatus.put("lastAttemptTimeStamp", 0);
			this.treasureStatuses.put(position, treasureStatus);
			return;
		}
		
		// 2) update the treasure
		// 2.1) check if our version is more recent
		HashMap<String, Object> treasureStatus = (HashMap<String, Object>) this.treasureStatuses.get(position);
		long lastUpdateTreasure = (long) treasureStatus.get("lastUpdate");
		if (lastUpdateTreasure > lastUpdate) {
			// our version is more recent, do not update
			return;
		}
		
		// 2.2) update the fields
        HashMap<String, Object> newTreasureStatus = new HashMap<String, Object>();
        newTreasureStatus.put("amount", amount);
        newTreasureStatus.put("type", type);
        newTreasureStatus.put("strengthRequirements", strengthRequirements);
        newTreasureStatus.put("lockPickingRequirements", lockPickingRequirements);
    	newTreasureStatus.put("lockIsOpen", lockIsOpen);
    	newTreasureStatus.put("lastUpdate", lastUpdate);
    	newTreasureStatus.put("assignement", treasureStatus.get("assignement"));
    	newTreasureStatus.put("assignedTo", treasureStatus.get("assignedTo"));
    	newTreasureStatus.put("assignementEndTimeStamp", treasureStatus.get("assignementEndTimeStamp"));
    	newTreasureStatus.put("numberOfAttemptsSoFar", treasureStatus.get("numberOfAttemptsSoFar"));
    	newTreasureStatus.put("lastAttemptTimeStamp", treasureStatus.get("lastAttemptTimeStamp"));
    	this.treasureStatuses.put(position, newTreasureStatus);
	}
	
	// COMMENTED because open nodes are now updated from ExploCoopBehaviour5's map automatically when reseting the tasks
//	/*
//	 * add/update an open node to the list of open nodes
//	 */
//	public void addOpenNode(String position) {
//		// 1) check if the open node is not already in the list
//		if (!this.openNodeStatuses.containsKey(position)) {
//			// add the open node
//			HashMap<String, Object> openNodeStatus = new HashMap<String, Object>();
//			openNodeStatus.put("assignement", "available");
//			openNodeStatus.put("numberOfAttemptsSoFar", 0);
//			openNodeStatus.put("lastAttemptTimeStamp", 0);
//			this.openNodeStatuses.put(position, openNodeStatus);
//			return;
//		}
//
//		// 2) update the open node
//		HashMap<String, Object> openNodeStatus = (HashMap<String, Object>) this.openNodeStatuses.get(position);
//		openNodeStatus.put("assignement", "available");
//		openNodeStatus.put("numberOfAttemptsSoFar", openNodeStatus.get("numberOfAttemptsSoFar"));
//		openNodeStatus.put("lastAttemptTimeStamp", openNodeStatus.get("lastAttemptTimeStamp"));
//		this.openNodeStatuses.put(position, openNodeStatus);
//	}

	/*
	 * update the open node status
	 */
	public void updateOpenNodeStatus() {
		// 1) get the open nodes from baseBehaviour
		List<String> openNodes = this.baseBehaviour.getMyMap().getOpenNodes();
		
		// 2) get the open nodes that are now closed
		List<String> openNodesToRemove = new ArrayList<String>();
		for (String position : this.openNodeStatuses.keySet()) {
			if (!openNodes.contains(position)) {
				// open node is not in the list anymore
				openNodesToRemove.add(position);
			}
		}
		
		// 3) remove the open nodes that are not in the list anymore
		for (String position : openNodesToRemove) {
			this.openNodeStatuses.remove(position);
		}
		
		// 4) get the new open nodes
		List<String> newOpenNodes = new ArrayList<String>();
		for (String position : openNodes) {
			if (!this.openNodeStatuses.containsKey(position)) {
				// open node is not in the list, add it
				newOpenNodes.add(position);
			}
		}
		
		// 5) add the new open nodes
		for (String position : newOpenNodes) {
			HashMap<String, Object> openNodeStatus = new HashMap<String, Object>();
			openNodeStatus.put("assignement", "available");
			openNodeStatus.put("numberOfAttemptsSoFar", 0);
			openNodeStatus.put("lastAttemptTimeStamp", 0);
			openNodeStatus.put("assignementEndTimeStamp", 0);
			this.openNodeStatuses.put(position, openNodeStatus);
		}
	}

	/*
	 * update the treasures status
	 */
	public void updateTreasureStatus() {
		// 1) get the treasures from baseBehaviour
		HashMap<String, TreasureInfo> treasures = this.baseBehaviour.getKnownTreasures();
		
		// 2) update the treasures
		for (String position : treasures.keySet()) {
			TreasureInfo t = treasures.get(position);
			updateTreasure(position, t.amount, t.type, t.requiredStrength, t.requiredLockpicking, t.lockIsOpen, t.timestamp);
		}
	}
	
	public HashMap<String, Object> newCommunication(HashMap<String, Object> content) {
		// 1) get the sender information
	    String senderName = (String) content.get("name");
	    HashMap<String, Object> senderStatus = new HashMap<String, Object>();
	    senderStatus.put("availability", "available");
	    senderStatus.put("shouldBeBackAt", 0);
	    senderStatus.put("ressourceType", content.get("ressourceType"));
	    senderStatus.put("strengthExpertise", content.get("strengthExpertise"));
	    senderStatus.put("lockPickingExpertise", content.get("lockPickingExpertise"));
	    senderStatus.put("backPackCapacityGold", content.get("backPackCapacityGold"));
	    senderStatus.put("backPackCapacityDiamond", content.get("backPackCapacityDiamond"));
	    this.agentStatuses.put(senderName, senderStatus);
	    
	    if (this.verbose) {
	    	System.out.println(this.myAgent.getLocalName() + " received a communication from " + senderName + ": " + content);
	    }
		
		// 2) create the new mission
	    HashMap<String, Object> newMission = getNewMission(senderName);
		
		// 3) return the content
		return newMission;
	}

	/*
	 * The code executed at each step
	 */
	@Override
	public void action() {
		// 0) if this is the first time action() is called, setup the agent
		if (!setupFlag)
			setup();	// sets up the map and adds the sharing behaviour

	}

	@Override
	public boolean done() {
		return finished;
	}

}
