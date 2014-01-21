// =================================================================                                                                   
// Copyright (C) 2011-2015 Pierre Lison (plison@ifi.uio.no)                                                                            
//                                                                                                                                     
// This library is free software; you can redistribute it and/or                                                                       
// modify it under the terms of the GNU Lesser General Public License                                                                  
// as published by the Free Software Foundation; either version 2.1 of                                                                 
// the License, or (at your option) any later version.                                                                                 
//                                                                                                                                     
// This library is distributed in the hope that it will be useful, but                                                                 
// WITHOUT ANY WARRANTY; without even the implied warranty of                                                                          
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU                                                                    
// Lesser General Public License for more details.                                                                                     
//                                                                                                                                     
// You should have received a copy of the GNU Lesser General Public                                                                    
// License along with this program; if not, write to the Free Software                                                                 
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA                                                                           
// 02111-1307, USA.                                                                                                                    
// =================================================================                                                                   

package opendial;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import opendial.arch.DialException;
import opendial.arch.Logger;
import opendial.arch.Settings;
import opendial.bn.distribs.IndependentProbDistribution;
import opendial.bn.distribs.discrete.CategoricalTable;
import opendial.datastructs.Assignment;
import opendial.domains.Domain;
import opendial.domains.Model;
import opendial.gui.GUIFrame;
import opendial.modules.DialogueImporter;
import opendial.modules.DialogueRecorder;
import opendial.modules.ForwardPlanner;
import opendial.modules.Module;
import opendial.modules.Simulator;
import opendial.readers.XMLDomainReader;
import opendial.readers.XMLInteractionReader;
import opendial.readers.XMLSettingsReader;
import opendial.state.DialogueState;
   
import org.apache.commons.collections15.ListUtils;
 
/**
 *  <p>Dialogue system based on probabilistic rules.  A dialogue system comprises: <ul>
 *  <li> the current dialogue state
 *  <li> the dialogue domain with a list of rule-structured models
 *  <li> the list of system modules
 *  <li> the system settings. 
 *  </ul>
 *  
 *  <p>After initialising the dialogue system, the system should be started with the
 *  method startSystem(). The system can be paused or resumed at any time. 
 *
 * @author  Pierre Lison (plison@ifi.uio.no)
 * @version $Date::                      $
 *
 */
public class DialogueSystem {

	// logger
	public static Logger log = new Logger("DialogueSystem", Logger.Level.DEBUG);

	// the dialogue state
	protected DialogueState curState;

	// the dialogue domain
	protected Domain domain;

	// the set of modules attached to the system
	protected List<Module> modules;

	// the system settings
	protected Settings settings;

	// whether the system is paused or active
	protected boolean paused = true;



	// ===================================
	//  SYSTEM INITIALISATION
	// ===================================


	/**
	 * Creates a new dialogue system with an empty dialogue system
	 * 
	 * @throws DialException if the system could not be created
	 */
	public DialogueSystem() throws DialException {
		settings = new Settings();
		curState = new DialogueState();
		
		// inserting standard modules
		modules = new ArrayList<Module>();
		modules.add(new GUIFrame(this));
		modules.add(new DialogueRecorder(this));
		modules.add(new ForwardPlanner(this));
	}


	/**
	 * Creates a new dialogue system with the provided dialogue domain
	 * 
	 * @param domain the dialogue domain to employ
	 * @throws DialException if the system could not be created
	 */
	public DialogueSystem(Domain domain) throws DialException {
		this();
		changeDomain(domain);
	}
	
	

	/**
	 * Starts the dialogue system and its modules.
	 */
	public void startSystem() {
		paused=false;
		for (Module module :new ArrayList<Module>(modules)) {
			try {
				if (!module.isRunning()) {
				module.start();
				}
				else {
					module.pause(false);
				}
			}
			catch (DialException e) {
				log.warning("could not start module " + module.getClass().getCanonicalName());
				modules.remove(module);
			}
		}
		synchronized (curState) {
			curState.setAsNew();
			update();
		}
	}


	/**
	 * Changes the dialogue domain for the dialogue domain
	 * 
	 * @param domain the dialogue domain to employ
	 * @throws DialException if the system could not be created
	 */
	public void changeDomain(Domain domain) throws DialException {
		changeSettings(domain.getSettings());
		this.domain = domain;
		curState = domain.getInitialState().copy();
		curState.setParameters(domain.getParameters());
		startSystem();
	}

  
	/**
	 * Attaches the module to the dialogue system.
	 * 
	 * @param module the module to add
	 */
	public void attachModule(Module module) {
		if (modules.contains(module) || getModule(module.getClass()) != null) {
			log.info("Module " + module.getClass().getCanonicalName() + " is already attached");
			return;
		}
		int pos = modules.indexOf(getModule(ForwardPlanner.class));
		modules.add(pos, module);
		if (!paused) {
			try {
				module.start();
			}
			catch (DialException e) {
				log.warning("could not start module " + module.getClass().getCanonicalName());
				modules.remove(module);
			}
		}
	}
	
	

	/**
	 * Attaches the module to the dialogue system.
	 * 
	 * @param module the module class to instantiate
	 */
	public <T extends Module> void attachModule(Class<T> module) {
		try {
			Constructor<T> constructor = module.getConstructor(DialogueSystem.class);
			attachModule(constructor.newInstance(this));
			recordComment("Module " + module.getSimpleName() + " successfully attached");
		} 
		 catch (InvocationTargetException e) {
			 log.warning("cannot attach module: " + e.getTargetException());
			 recordComment("cannot attach module: " + e.getTargetException());
			}
		catch (Exception e) {
			log.warning("cannot attach module of class " + module.getCanonicalName() + ": " + e);
		}
	}

 
	/**
	 * Detaches the module of the dialogue system.  If the module is
	 * not included in the system, does nothing.
	 * 
	 * @param module the module to detach
	 */
	public void detachModule(Class<? extends Module> moduleClass) {
		Module module = getModule(moduleClass);
		if (module != null) {
			modules.remove(module);
		}
	}



	/**
	 * Pauses or resumes the dialogue system.
	 * 
	 * @param shouldBePaused whether the system should be paused or resumed.
	 */
	public void pause(boolean shouldBePaused) {
		paused = shouldBePaused;
		recordComment((shouldBePaused)? "system paused" : "system resumed");

		for (Module module : modules) {
			module.pause(shouldBePaused);
		}
		if (!shouldBePaused && !curState.getNewVariables().isEmpty()) {
			 synchronized (curState) {
						update();
				}
			}
	}


	/**
	 * Adds a comment on the GUI and the dialogue recorder.
	 * 
	 * @param comment the comment to record
	 */
	public void recordComment(String comment) {
		if (getModule(GUIFrame.class)!= null && getModule(GUIFrame.class).isRunning()) {
			getModule(GUIFrame.class).addComment(comment);
		}
		if (getModule(DialogueRecorder.class)!= null && getModule(DialogueRecorder.class).isRunning()) {
			getModule(DialogueRecorder.class).addComment(comment);
		}
	}
	

	/**
	 * Changes the settings of the system
	 * 
	 * @param settings the new settings
	 */
	public void changeSettings(Settings settings) {
		
		List<Class<Module>> modsToDetach = ListUtils.subtract(this.settings.modules, settings.modules);
		List<Class<Module>> modsToAttach = ListUtils.subtract(settings.modules, this.settings.modules);
		
		for (Class<Module> toDetach : modsToDetach) {
			detachModule(toDetach);
		}
		this.settings.fillSettings(settings.getFullMapping());

		for (Class<Module> toAttach : modsToAttach) {
			log.info("Attaching module: " + toAttach.getCanonicalName());
			attachModule(toAttach);
		}	
	}

	
	// ===================================
	//  STATE UPDATE
	// ===================================


	/**
	 * Adds the content (expressed as a categorical table over variables) to the
	 * current dialogue state, and subsequently updates the dialogue state.
	 * 
	 * @param table the categorical table to add
	 * @throws DialException if the state could not be updated.
	 */
	public void addContent(CategoricalTable table) throws DialException {
		if (!paused) {
			synchronized (curState) {
				curState.addToState(table);
				update();
			}
		}
		else {
			log.info("system is currently paused -- ignoring content " + table );
		}
	}

	/**
	 * Adds the content (expressed as a certain assignment over variables) to the
	 * current dialogue state, and subsequently updates the dialogue state.
	 * 
	 * @param table the categorical table to add
	 * @throws DialException if the state could not be updated.
	 */
	public void addContent(Assignment assign) throws DialException {
		addContent(new CategoricalTable(assign));
	}


	/**
	 * Merges the dialogue state included as argument into the current one, and
	 * updates the dialogue state.
	 * 
	 * @param newState the state to merge into the current state
	 * @throws DialException if the 
	 */
	public void addContent(DialogueState newState) throws DialException {
		if (!paused) {
			synchronized (curState) {
				curState.addToState(newState);
				update();
			}
		}
		else {
			log.info("system is currently paused -- ignoring content " + newState);
		}
	}

	

	/**
	 * Performs an update loop on the current dialogue state, by triggering 
	 * all the models and modules attached to the system until all possible 
	 * updates have been performed.  The dialogue state is pruned.
	 */
	protected void update() {
		
		while (!curState.getNewVariables().isEmpty()) {
			
			Set<String> toProcess = curState.getNewVariables();
			curState.reduce();	
			
			for (Model model : domain.getModels()) {
					model.trigger(curState, toProcess);
			}
			
			for (Module module : modules) {
				module.trigger(curState, toProcess);
			}
		}
	}
	
	


	// ===================================
	//  GETTERS
	// ===================================


	/**
	 * Returns the current dialogue state for the dialogue system.
	 * 
	 * @return the dialogue state
	 */
	public DialogueState getState() {
		return curState;
	}
	
	/**
	 * Returns the probability distribution associated with the variables in the
	 * current dialogue state.
	 * 
	 * @param variables the variables to query
	 * @return the resulting probability distribution for these variables
	 */
	public IndependentProbDistribution getContent(String... variables) {
		return curState.queryProb(variables);
	}
	
	/**
	 * Returns the probability distribution associated with the variables in the
	 * current dialogue state.
	 * 
	 * @param variables the variables to query
	 * @return the resulting probability distribution for these variables
	 */
	public IndependentProbDistribution getContent(Collection<String> variables) {
		return curState.queryProb(variables);
	}


	/**
	 * Returns the module attached to the dialogue system and belonging to
	 * a particular class, if one exists.  If no module exists, returns null
	 * 
	 * @param cls the class.
	 * @return the attached module of that class, if one exists.
	 */
	@SuppressWarnings("unchecked")
	public <T extends Module> T getModule(Class<T> cls) {
		for (Module mod : new ArrayList<Module>(modules)) {
			if (mod.getClass().equals(cls)) {
				return (T)mod;
			}
		}
		return null;
	}


	/**
	 * Returns true is the system is currently paused, and false otherwise
	 * 
	 * @return true if paused, false otherwise.
	 */
	public boolean isPaused() {
		return paused;
	}


	/**
	 * Returns the settings for the dialogue system.
	 * 
	 * @return the system settings.
	 */
	public Settings getSettings() {
		return settings;
	}


	/**
	 * Returns the domain for the dialogue system.
	 * 
	 * @return the dialogue domain.
	 */
	public Domain getDomain() {
		return domain;
	}

	/**
	 * Returns the collection of modules attached to the system
	 * 
	 * @return the modules
	 */
	public Collection<Module> getModules() {
		return new ArrayList<Module>(modules);
	}


	// ===================================
	//  MAIN METHOD
	// ===================================


	public static void main(String[] args) {
		try {
			DialogueSystem system = new DialogueSystem();
			String domainFile = System.getProperty("domain");
			String settingsFile = System.getProperty("settings");
			String dialogueFile = System.getProperty("dialogue");
			String simulatorFile = System.getProperty("simulator");
			
			if (domainFile != null) {
				system.changeDomain(XMLDomainReader.extractDomain(domainFile));
				log.info("Domain from " + domainFile + " successfully extracted");
			}
			if (settingsFile != null) {
				system.getSettings().fillSettings(XMLSettingsReader.extractMapping(settingsFile));
				log.info("Settings from " + settingsFile + " successfully extracted");		
			}
			if (dialogueFile != null) {
				List<DialogueState> dialogue = XMLInteractionReader.extractInteraction(dialogueFile);
				log.info("Interaction from " + dialogueFile + " successfully extracted");		
				(new DialogueImporter(system, dialogue)).start();
			}
			if (simulatorFile != null) {
				Simulator simulator = new Simulator(system, XMLDomainReader.extractDomain(simulatorFile));
				log.info("Simulator with domain " + simulatorFile + " successfully extracted");		
				system.attachModule(simulator);
			}
			system.getSettings().fillSettings(System.getProperties());
			system.changeSettings(system.getSettings());
			system.startSystem();
		}
		catch (DialException e) {
			log.severe("could not start system, aborting: " + e);
		}
	}




}