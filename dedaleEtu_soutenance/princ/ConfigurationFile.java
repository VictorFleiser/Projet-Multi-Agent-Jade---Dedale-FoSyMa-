package eu.su.mas.dedaleEtu.princ;

import eu.su.mas.dedale.env.EnvironmentType;
import eu.su.mas.dedale.env.GeneratorType;

/**
 * Configuration file for a Dedale instance 
 * 1) Network and platform parameters
 * 2) Environment parameters 
 * 
 * 
 * @author hc
 *
 */
public final class ConfigurationFile {


	/************************************
	 ***********************************
	 *
	 * 1) Network and platform parameters
	 * 
	 ***********************************/
	
	//Distributed or not, and is the current computer in charge of the main-container
	public static boolean PLATFORMisDISTRIBUTED= false;
	public static boolean COMPUTERisMAIN= true;

	//network configuration
	public static String PLATFORM_HOSTNAME="127.0.0.1";
	public static String PLATFORM_ID="Ithaq";
	public static Integer PLATFORM_PORT=8887;
	
	//List of containers to be created on the current computer
	public static String LOCAL_CONTAINER_NAME=PLATFORM_ID+"_"+"container1";
	public static String LOCAL_CONTAINER2_NAME=PLATFORM_ID+"_"+"container2";
	public static String LOCAL_CONTAINER3_NAME=PLATFORM_ID+"_"+"container3";
	public static String LOCAL_CONTAINER4_NAME=PLATFORM_ID+"_"+"container4";


	/************************************
	 ************************************
	 *
	 * 2) Environment parameters 
	 * 
	 ************************************/

	/**
	 * The environment is either a GraphStream (2D discrete) or JME (3D continuous) one.
	 */
	public static EnvironmentType ENVIRONMENT_TYPE=EnvironmentType.GS;
	
	/**
	 * The environment is either manually designed, or generated with a specific generator
	 */
	public static GeneratorType GENERATOR_TYPE=GeneratorType.MANUAL;
//	public static GeneratorType GENERATOR_TYPE=GeneratorType.GS_GRID;
	/**
	 * 	The GateKeeper is in charge of the Platform and of the agents within, do not change its name.
	 */
	public static String DEFAULT_GATEKEEPER_NAME="GK";


	/************************************
	 *
	 * 2-a) Environment parameters when the environment is loaded. We need :
	 *  - a topology, 
	 *  - the configuration of the elements on the map,
	 *
	 *  These parameters must be empty if the environment is generated or already online 
	 *****************************/
	
	/**
	 * Give the topology 
	 */
//	public static String INSTANCE_TOPOLOGY=null;
	
	//public static String INSTANCE_TOPOLOGY="resources/topology/map2020-topologyExam1-graph.dgs";
	//public static String INSTANCE_TOPOLOGY="resources/topology/HouatTopology";
//	public static String INSTANCE_TOPOLOGY="resources/topology/map2018-topology"; // par défaut
	//public static String INSTANCE_TOPOLOGY="resources/topology/map2021-topologyExam1-tree.dgs";
	//public static String INSTANCE_TOPOLOGY="resources/interlocking/mapInterlocking2-topology";
	//public static String INSTANCE_TOPOLOGY="resources/topology/map2023-topologyExam.dgs";
	//public static String INSTANCE_TOPOLOGY="resources/topology/map2020-topologyExam1-graph.dgs";
	//public static String INSTANCE_TOPOLOGY="resources/map2018-topology";
	//public static String INSTANCE_TOPOLOGY="resources/topology/map2022-topologyExam1-draft.dgs";
	//public static String INSTANCE_TOPOLOGY="resources/topology/map2022-topologyExam-V2b.dgs";
	//public static String INSTANCE_TOPOLOGY="resources/map2019-topologyExam1";

//	public static String INSTANCE_TOPOLOGY="resources/topology/test_3";
	
	public static String INSTANCE_TOPOLOGY="resources/topology/map2025-topologyExam"; // DEMO


	
	/**
	 * Give the elements available on the map, if any
	 */
	// If the environment is loaded but you do not want to define elements on the map
//	public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/distributedExploration/emptyMap";
	
	// otherwise
	//public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/treasureHunt/map2019-elementsExam1";
	//public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/treasureHunt/Houat-elements";
//	public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/treasureHunt/map2018-elements.json";
	//public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/interlocking/mapInterlocking2-elements";
	//public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/map2018-elements";
	//public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/mapInterlocking2-elements";
	//public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/treasureHunt/map2023-elements";
	
	public static String INSTANCE_CONFIGURATION_ELEMENTS="resources/treasureHunt/map2025-elements.json";

	
	/************************************
	 * 
	 * 
	 * 2-b) Environment parameters when it is generated 
	 * 
	 * 
	 ***********************************/

	/**
	 * Size of the generated environment, mandatory
	 */
	public static Integer ENVIRONMENT_SIZE=4;
	// Parameters required for some generators (see dedale.gitlab.io)
	public static Integer OPTIONAL_ADDITIONAL_ENVGENERATOR_PARAM1=1;//used by the BARABASI_ALBERT generator to know the number of childs
	public static Integer[] GENERATOR_PARAMETERS= {ENVIRONMENT_SIZE,OPTIONAL_ADDITIONAL_ENVGENERATOR_PARAM1};

	/**
	 * Wumpus proximity detection radius
	 */
	public static final Integer DEFAULT_DETECTION_RADIUS = 1;


	/**
	 * 	Agents communication radius
	 */
	public static Integer DEFAULT_COMMUNICATION_REACH=3;

	/**
	 * Elements on the map
	 */
	
	public static boolean ACTIVE_WELL=false;
	public static boolean ACTIVE_GOLD=false;
	public static boolean ACTIVE_DIAMOND=false;

	/************************************
	 ************************************
	 *
	 * 3) Agents characteristics
	 * 
	 ************************************/

	/**
	 * Must'nt be null as it describes the native agents' capabilities 
	 */
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agent-3tests.json";
//	public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agent-3tests-silo-golem.json";
//	public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agent-2-of-each.json";
//	public static String INSTANCE_CONFIGURATION_ENTITIES="agent-3collect-1explo-2tanker-2-golem.json";
//	public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agent-test.json";
	
	public static String INSTANCE_CONFIGURATION_ENTITIES="resources/map2025-entities.json";	//DEMO
	
	//public static String INSTANCE_CONFIGURATION_ENTITIES=null;
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agentExplo.json";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agentExplo-2.json";
//	public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agent-2collect.json";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agentExploCoop-2.json";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/treasureHunt/map2023-entities.json";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/treasureHunt/agentTankerCollect.json";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/agentKeyboardControlled";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/hunt/map2020-entitiesTree";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/hunt/map2021-entitiesTree";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/hunt/map2020-entitiesGraph";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/hunt/map2021-entitiesGraph";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/map2018-entities2";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/map2019-entitiesExam1";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/monoAgent-entities";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="resources/mapInterlocking2-entities";
	//public static String INSTANCE_CONFIGURATION_ENTITIES=null;
	


	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/map2018-agentExplo";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/agentExploSolo";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/agentExploCoop-2";	
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/map2018-agentTanker";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/map2018-agentCollect";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/map2018-entities";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/map2018-agentGolem";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/map2018-agentTankerCollect";
	//public static String INSTANCE_CONFIGURATION_ENTITIES="src/test/java/resources/map2019-entitiesExam1";

}
