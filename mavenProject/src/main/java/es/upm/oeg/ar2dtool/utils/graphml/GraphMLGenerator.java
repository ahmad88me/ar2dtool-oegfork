package es.upm.oeg.ar2dtool.utils.graphml;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;

import com.hp.hpl.jena.ontology.DatatypeProperty;
import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

import es.upm.oeg.ar2dtool.exceptions.NullTripleMember;
import es.upm.oeg.ar2dtool.logger.AR2DToolLogger;
import es.upm.oeg.ar2dtool.utils.AR2DTriple;
import es.upm.oeg.ar2dtool.utils.ConfigValues;
import es.upm.oeg.ar2dtool.utils.dot.ObjPropPair;


public class GraphMLGenerator 
{
	
	//POPULAR URIS
	private static final String RDFS_RANGE = "http://www.w3.org/2000/01/rdf-schema#range";
	private static final String RDFS_DOMAIN = "http://www.w3.org/2000/01/rdf-schema#domain";
	
	//WHEN RANGE OR DOMAINS ARE EMPTY
	private static final String DEFAULT_OBJ_PROP_VALUE = "http://www.w3.org/2002/07/owl#Thing";

	
	//OBJ PROP LIST
	private Map<String,ObjPropPair<String,String>> objPropsMap;
	

	// LOGGING
	private static final AR2DToolLogger log = AR2DToolLogger.getLogger("AR2DTOOL");
	
	//DEFAULT SHAPES AND COLORS
	private static final String DEFAULT_NODE_COLOR = "white";
	private static final String DEFAULT_NODE_SHAPE = "rectangle";
	private static final String DEFAULT_EDGE_COLOR = "black";
	
	//ID PREFIXES
	private static final String NODE_ID_PREFIX = "nid_";
	private static final String EDGE_ID_PREFIX = "eid_";


	//CONF VALUES
	private ConfigValues conf;
		
	//ONTOLOGY MODEL
	private OntModel model;
	
	//DOT Triples
	private ArrayList<AR2DTriple> gmltriples;
	
	//RESTRICTION LIST
	private ArrayList<String> restrictionList;

	//AVOID RESTRICTIONS
	private static final boolean AVOID_RESTRICTION_NODES = true;

	
	
	//SHAPES&COLORS LISTS
	
	private ArrayList<String> classesSC, individualsSC, literalsSC, ontPropertiesSC, dtPropertiesSC;

	private Map<String, String> prefixMap;

	
	public GraphMLGenerator(OntModel m, ConfigValues c, ArrayList<String> clsc, Map<String, String> pm,  ArrayList<String> reslist)
	{
		model = m;
		conf = c;
		gmltriples = new ArrayList<AR2DTriple>();
		classesSC = clsc; //new ArrayList<String>();
		individualsSC = new ArrayList<String>();
		literalsSC = new ArrayList<String>();
		ontPropertiesSC = new ArrayList<String>();
		dtPropertiesSC = new ArrayList<String>();
		objPropsMap = new HashMap<String,ObjPropPair<String,String>>();
		restrictionList = reslist;
		prefixMap = pm;
	}
	
	/*
	 * This method traverses all the DOT triples on the 'model' field
	 * applying the transformations specified on:
	 * 
	 * 
	 * - shapes and colors
	 * - special elements list
	 * - sintetyze obj props
	 * - node names mode
	 * 
	 */
	public void applyTransformations() throws NullTripleMember
	{	
		//detecting classes
		detectClasses();
		
		//detecting individuals
		detectIndividuals();
		
		//detecting ont properties
		detectOntProperties();
		
		//detecting dt properties
		detectDtProperties();
		
		
		
		StmtIterator it = model.listStatements();
		while(it.hasNext())
		{
			Statement st = it.next();
			
			Resource s = st.getSubject();
			Property p = st.getPredicate();
			RDFNode o = st.getObject();
			
			if((AVOID_RESTRICTION_NODES)&&(restrictionList.contains(s.toString())))
				continue;
			
			//detecting literals
			if(o.isLiteral())
			{
				literalsSC.add(st.getObject().toString());
			}
			else
			{
				if((AVOID_RESTRICTION_NODES)&&(restrictionList.contains(o.toString())))
					continue;
			}

			
			//check syntetize ob props
			//if it is an obj prop and the user wants to sysntetize we will add it later
			if(conf.synthesizeObjectProperties() && checkObjPropoerties(s,p,o))
				continue;
			
			
			
			String sName = getNodeName(s);
			String pName = getNodeName(p);
			String oName = getNodeName(o);
			
			gmltriples.add(new AR2DTriple(sName,oName,pName));
			
		}
		
		generateSyntObjPropertiesTriples();
		
		log(printGmlDriples());
		
	
	}
	
	

	private String printGmlDriples() 
	{
		String res = "----- GML Triples -----\n";
		for(AR2DTriple dt : gmltriples)
		{
			res +=dt.toString();
		}
		
		
		return res + "----- End GML Triples -----\n";
	}

	public String generateGraphMLSource()
	{
		String res ="";
		
		//a list to avoid duplicated generation of nodes
		LinkedHashSet<String> generatedNodes = new LinkedHashSet<String>();
		
		
		int edgeCounter = 0;
		for(AR2DTriple gt : gmltriples)
		{
			String source = gt.getSource();
			String target = gt.getTarget();
			String edge = gt.getEdge();
			
			
			//generate source node if necessary
			if(!generatedNodes.contains(source))
			{
				log("Generating node for " + source);
				res += getNode(source,getNodeColor(source), getNodeShape(source));
				generatedNodes.add(source);
			}


			//generate source target if necessary
			if(!generatedNodes.contains(target))
			{
				log("Generating node for " + target);
				res += getNode(target,getNodeColor(source), getNodeShape(source));
				generatedNodes.add(target);
			}
			

			log("Generating edge " + edge + " from " + source + " to "  + target);
			res+=getEdge(edge, edgeCounter, source, target, getEdgeColor(source));
			edgeCounter++;
			
		}
		
		res=getGraphMLHeader(edgeCounter,generatedNodes.size()) + res;
		
		res+=getGraphMLTail();
		
		return res;
	}
	
	
	/*
	 * 
	 * 
		keys.put("classColor","#000000");
		keys.put("individualColor","#000000");
		keys.put("literalColor","#000000");
		keys.put("arrowColor","#000000");
		 classesSC, individualsSC, literalsSC, ontPropertiesSC, dtPropertiesSC;
		
		keys.put("classShape","rectangle");
		keys.put("individualShape","rectangle");
		keys.put("literalShape","rectangle");
	 */
	private String getNodeColor(String source) 
	{
		if(classesSC.contains(source))
		{
			//TODO remove
			log("Class found!" + source + " color " + conf.getKeys().get("classColor"));
			return conf.getKeys().get("classColor");
		}
		if(individualsSC.contains(source))
		{
			return conf.getKeys().get("individualColor");
		}
		if(literalsSC.contains(source))
		{
			return conf.getKeys().get("literalColor");
		}
		
		
		return DEFAULT_NODE_COLOR;
	}

	
	private String getNodeShape(String source) 
	{
		if(classesSC.contains(source))
		{
			return conf.getKeys().get("classShape");
		}
		if(individualsSC.contains(source))
		{
			return conf.getKeys().get("individualShape");
		}
		if(literalsSC.contains(source))
		{
			return conf.getKeys().get("literalShape");
		}
		
		return DEFAULT_NODE_SHAPE;
	}
	
	private String getEdgeColor(String source)
	{
		if(ontPropertiesSC.contains(source))
		{
			return conf.getKeys().get("arrowColor");
		}
		if(dtPropertiesSC.contains(source))
		{
			return conf.getKeys().get("arrowColor");
		}
		//TODO ontPropertiesSC, dtPropertiesSC
		return DEFAULT_EDGE_COLOR;
	}

	public void saveSourceToFile(String path) 
	{
		try {
			PrintWriter out = new PrintWriter(path);
			String src = this.generateGraphMLSource();
			out.println(src);
			out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	}
	
	
	private void generateSyntObjPropertiesTriples() throws NullTripleMember 
	{
		if(!conf.synthesizeObjectProperties())
			return;
		
		Iterator<Entry<String, ObjPropPair<String, String>>> it = objPropsMap.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry<String,ObjPropPair<String,String>> kv = (Map.Entry<String,ObjPropPair<String,String>>)it.next();
	        String propUri = (String) kv.getKey();
	        
	        ObjPropPair<String,String> mp = (ObjPropPair<String, String>) kv.getValue();
	        String rangeUri = mp.getRight();
	        String domainUri = mp.getLeft();

			String domainName = getNodeName(domainUri);
			String rangeName = getNodeName(rangeUri);
			String propName = getNodeName(propUri);
			
			gmltriples.add(new AR2DTriple(domainName,rangeName,propName));
			
	    }
	}

	private boolean checkObjPropoerties(Resource s,Property p,RDFNode o) 
	{
		if(p.getURI().equals(RDFS_DOMAIN))
		{
			ObjPropPair<String, String> dr = new ObjPropPair<String,String>();
			if(objPropsMap.containsKey(s.getURI()))
			{
				dr = objPropsMap.get(s.getURI());
			}
			else
			{
				dr.setRight(DEFAULT_OBJ_PROP_VALUE);
			}
			
			String oString = o.asResource().getURI();
			dr.setLeft(oString);
			objPropsMap.put(s.getURI(), dr);
			return true;
		}
		

		if(p.getURI().equals(RDFS_RANGE))
		{
			ObjPropPair<String, String> dr = new ObjPropPair<String,String>();
			if(objPropsMap.containsKey(s.getURI()))
			{
				dr = objPropsMap.get(s.getURI());
			}
			else
			{
				dr.setLeft(DEFAULT_OBJ_PROP_VALUE);	
			}
			

			String oString = o.asResource().getURI();
			dr.setRight(oString);
			objPropsMap.put(s.getURI(), dr);
			return true;
		}
		
		
			
		return false;
	}

	
	private String getNodeName(String n) 
	{
		return getNodeName(model.getResource(n));
	}
	
	
	private String getNodeName(RDFNode n) 
	{
		if(n.isLiteral())
			return n.toString();

		//at this point we know that we are dealing with a resource
		Resource res = n.asResource();
		switch (conf.getNodeNameMode()) 
		{
			case LOCALNAME:
			{
				return res.getLocalName();
			}
			case PREFIX:
			{
				String ns = res.getNameSpace();
				
				String prefix = prefixMap.get(ns);
				log(n + " looking for namespace " + ns + " found " + prefix);
				log("pfxmap" + prefixMap.keySet());
				if(prefix==null)
				{
					//if the prefix does not exit we use the full URI
					return res.getURI();
				}
				else
				{
					//replace the ns with the prefix		
					log("ReturnPrefix" + res.getURI().replace(ns, prefix+":"));
					return res.getURI().replace(ns, prefix+":");
				}
			}
			default:
			break;
		}

		//at this point we know the user wants to use URIs
		return res.getURI();
	}


	private void detectDtProperties() 
	{
		ExtendedIterator<OntProperty> it = model.listOntProperties();
		while(it.hasNext())
		{
			ontPropertiesSC.add(getNodeName(it.next()));
		}
	}


	private void detectOntProperties() 
	{
		ExtendedIterator<DatatypeProperty> it = model.listDatatypeProperties();
		while(it.hasNext())
		{
			dtPropertiesSC.add(getNodeName(it.next()));
		}	
	}


	private void detectIndividuals() 
	{
		ExtendedIterator<Individual> it = model.listIndividuals();
		while(it.hasNext())
		{
			individualsSC.add(getNodeName(it.next()));
		}
	}


	private void detectClasses() 
	{
		
		ArrayList<String> res = new ArrayList<String>();
		for(String c: classesSC)
		{
			res.add(getNodeName(c));
		}
		
		classesSC = res;
		
		if(classesSC.isEmpty())
			{
			log("No classes detected");
		}
		else
		{
			log("Classes detected: " + classesSC);
		}
		
		
		//TODO remove
//		ExtendedIterator<OntClass> it = model.listClasses();
//		boolean empty = true;
//		while(it.hasNext())
//		{
//			empty = false;
//			classesSC.add(getNodeName(it.next()));
//		}
//		
//		if(empty)
//		{
//			log("No classes detected");
//		}
//		else
//		{
//			log("Classes detected: " + classesSC);
//		}
	}


	public ConfigValues getConf() {
		return conf;
	}


	public void setConf(ConfigValues conf) {
		this.conf = conf;
	}


	public OntModel getModel() {
		return model;
	}


	public void setModel(OntModel model) {
		this.model = model;
	}	
	
	private void log(String msg)
	{
		log.getWriter().log(msg);
	}
	
	private String getGraphMLHeader(int edges, int nodes)
	{
		String head = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns/graphml\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:y=\"http://www.yworks.com/xml/graphml\" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns/graphml http://www.yworks.com/xml/schema/graphml/1.0/ygraphml.xsd\">\n" +
				"   <key for=\"node\" id=\"d0\" yfiles.type=\"nodegraphics\" />\n" +
				"   <key attr.name=\"description\" attr.type=\"string\" for=\"node\" id=\"d1\" />\n" +
				"   <key for=\"edge\" id=\"d2\" yfiles.type=\"edgegraphics\" />\n" +
				"   <key attr.name=\"description\" attr.type=\"string\" for=\"edge\" id=\"d3\" />\n" +
				"   <key for=\"graphml\" id=\"d4\" yfiles.type=\"resources\" />\n" +
				"   <graph edgedefault=\"directed\" id=\"G\" parse.edges=\""+edges+"\" parse.nodes=\""+nodes+"\" parse.order=\"free\">\n";
		return head;
	}
	
	private String getGraphMLTail()
	{
		String tail = "   </graph>\n" +
					"</graphml>";
		return tail;
	}
	
	private String getNode(String nodeLabel, String nodeColor, String nodeShape)
	{
		String hexNodeColor = getHexColorCode(nodeColor);
		
		if(nodeLabel==null)
			nodeLabel="null";
		
		double widthScaleFactor = 10;
		
		int l = nodeLabel.length();
		
		if(l==0)
		{
			l=1;
		}
		double width = l * widthScaleFactor;
		
		String node = "      <node id=\""+NODE_ID_PREFIX+nodeLabel+"\">\n" +
				"         <data key=\"d0\">\n" +
				"            <y:ShapeNode>\n" +
				"               <y:Geometry height=\"30.0\" width=\"" + width + "\" x=\"0.0\" y=\"0.0\" />\n" +
				"               <y:Fill color=\"" + hexNodeColor + "\" transparent=\"false\" />\n" +
				"               <y:BorderStyle color=\"#000000\" type=\"line\" width=\"1.0\" />\n" +
				"               <y:NodeLabel alignment=\"center\" autoSizePolicy=\"content\" fontFamily=\"Dialog\" " +
				"fontSize=\"12\" fontStyle=\"plain\" hasBackgroundColor=\"false\" hasLineColor=\"false\" modelName=\"internal\" modelPosition=\"c\" " +
				"textColor=\"#000000\" visible=\"true\">" + nodeLabel + "</y:NodeLabel>\n" +
				"               <y:Shape type=\""+nodeShape+"\" />\n" +
				"            </y:ShapeNode>\n" +
				"         </data>\n" +
				"         <data key=\"d1\" />\n" +
				"      </node>\n";
		
		return node;
	}

	//black (default), red, blue, green, orange, yellow
	private String getHexColorCode(String nodeColor) 
	{
		if(nodeColor.equals("black"))
		{
			return "#000000";
		}
		if(nodeColor.equals("red"))
		{
			return "#FF0000";
		}
		if(nodeColor.equals("blue"))
		{
			return "#0000FF";
		}
		if(nodeColor.equals("green"))
		{
			return "#00FF00";
		}
		if(nodeColor.equals("orange"))
		{
			return "#FFA500";
		}
		if(nodeColor.equals("yellow"))
		{
			return "#FFFF00";
		}
		if(nodeColor.equals("white"))
		{
			return "#FFFFFF";
		}
		
		return "#00000000";
	}

	private String getEdge(String edgeLabel, int edgeCounter, String source, String target, String edgeColor)
	{
		String edge = "    <edge id=\""+EDGE_ID_PREFIX+edgeLabel+edgeCounter+"\" source=\""+NODE_ID_PREFIX+source+"\" target=\""+NODE_ID_PREFIX+target+"\">\n" +
				"         <data key=\"d2\">\n" +
				"            <y:PolyLineEdge>\n" +
				"               <y:LineStyle color=\""+edgeColor+"\" type=\"line\" width=\"1.0\" />\n" +
				"               <y:Arrows source=\"none\" target=\"normal\" />\n" +
				"               <y:EdgeLabel alignment=\"center\" distance=\"2.0\" fontFamily=\"Dialog\" fontSize=\"12\" fontStyle=\"plain\" " +
				"hasBackgroundColor=\"false\" hasLineColor=\"false\" modelName=\"six_pos\" modelPosition=\"tail\" preferredPlacement=\"anywhere\" " +
				"ratio=\"0.5\" textColor=\"#000000\" visible=\"true\">" + edgeLabel + "</y:EdgeLabel>\n" +
				"               <y:BendStyle smoothed=\"false\" />\n" +
				"            </y:PolyLineEdge>\n" +
				"         </data>\n" +
				"         <data key=\"d3\" />\n" +
				"      </edge>\n";
		
		return edge;
	}
	
}
