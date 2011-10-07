// =================================================================                                                                   
// Copyright (C) 2011-2013 Pierre Lison (plison@ifi.uio.no)                                                                            
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

package opendial.readers;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import opendial.arch.DialConstants.BinaryOperator;
import opendial.arch.DialException;
import opendial.domains.Domain;
import opendial.domains.Model;
import opendial.domains.realisations.SurfaceRealisation;
import opendial.domains.rules.Case;
import opendial.domains.rules.Rule;
import opendial.domains.rules.conditions.BasicCondition;
import opendial.domains.rules.conditions.ComplexCondition;
import opendial.domains.rules.conditions.Condition;
import opendial.domains.rules.conditions.VoidCondition;
import opendial.domains.rules.effects.AssignEffect;
import opendial.domains.rules.effects.ComplexEffect;
import opendial.domains.rules.variables.EntityVariable;
import opendial.domains.rules.variables.FeatureVariable;
import opendial.domains.rules.variables.FixedVariable;
import opendial.domains.rules.variables.PointerVariable;
import opendial.domains.triggers.SurfaceTrigger;
import opendial.domains.types.ActionType;
import opendial.domains.types.EntityType;
import opendial.domains.types.FeatureType;
import opendial.domains.types.FixedVariableType;
import opendial.domains.types.ObservationType;
import opendial.domains.types.values.RangeValue;
import opendial.domains.types.values.Value;
import opendial.state.Fluent;
import opendial.utils.Logger;

import org.junit.Before;
import org.junit.Test;
 

/**
 *  Testing for the XML Reader of dialogue domains.
 *
 * @author  Pierre Lison (plison@ifi.uio.no)
 * @version $Date::                      $
 *
 */ 
public class XMLDomainTest {

	static Logger log = new Logger("XMLDomainTest", Logger.Level.DEBUG);

	public String dialDomain = "domains//testing//microdom2.xml";
	Domain domain;
	
	
	@Before
	public void openDomain() throws DialException {
		XMLDomainReader reader = new XMLDomainReader();
		domain = reader.extractDomain(dialDomain);
	}
	
	
	@Test
	public void validationTest() throws IOException {
		
		boolean isValidated = XMLDomainValidator.validateXML(dialDomain);
		assertTrue(isValidated);
	}
	
	
	@Test
	public void entityExtraction() throws DialException {
		
		log.debug("number of entity type: " + domain.getEntityTypes().size());
		assertEquals(2, domain.getEntityTypes().size());
		
		EntityType firstType = domain.getEntityType("intent");
		assertEquals("intent", firstType.getName());
		log.debug("number of values: " + firstType.getValues().size());
		assertEquals(1, firstType.getValues().size());
		assertEquals("Want", firstType.getValues().get(0).getLabel());
		assertEquals(1, firstType.getPartialFeatures("Want").size());
		assertEquals(2, firstType.getPartialFeatures("Want").get(0).getValues().size());
		
		EntityType secondType = domain.getEntityType("robot");
		assertEquals("robot", secondType.getName());
		assertEquals(0, secondType.getValues().size());
		assertEquals(1, secondType.getFeatures().size());
		FeatureType featType = (FeatureType)secondType.getFeature("name");
		assertEquals(1, featType.getValues().size());
		assertTrue(featType.getValues().get(0) instanceof RangeValue);
		assertEquals("string", ((RangeValue)featType.getValues().get(0)).getRange());
		
	}
	 
	@Test
	public void fixedVariableExtraction() throws DialException {

		FixedVariableType thirdType = domain.getFixedVariableType("a_u");
		assertEquals("a_u", thirdType.getName());
		assertEquals(4, thirdType.getValues().size());
		assertEquals(0, thirdType.getFullFeatures().size());
		Value val = thirdType.getValues().get(1);
		assertEquals("AskFor", val.getLabel());
		assertEquals(1, thirdType.getPartialFeatures("AskFor").size());
		assertEquals(2, thirdType.getPartialFeatures("AskFor").get(0).getValues().size());
		assertEquals("X", thirdType.getPartialFeatures("AskFor").get(0).getValues().get(1).getLabel());
		
	}
	

	
	@Test
	public void observationExtraction() throws IOException, DialException {

		List<ObservationType> observations = domain.getObservationTypes();
		assertEquals(6, observations.size());
		ObservationType firstObservation = observations.get(0);
		assertTrue(firstObservation.getTrigger() instanceof SurfaceTrigger);
		assertEquals("doYObs", firstObservation.getName());
		assertEquals("do Y", ((SurfaceTrigger)firstObservation.getTrigger()).getContent());
	}

	@Test
	public void actionExtraction() throws IOException, DialException {

		List<ActionType> actions = domain.getActionTypes();
		assertEquals(1, actions.size());
		ActionType mainAction = actions.get(0);
		
		assertEquals(6, mainAction.getActionValues().size());
		log.debug(actions.size());

		assertTrue(mainAction.getActionValues().get(0) instanceof SurfaceRealisation);
		assertEquals("AskRepeat", mainAction.getActionValues().get(0).getLabel());
		assertEquals("OK, doing X!", ((SurfaceRealisation)mainAction.getActionValue("DoX")).getContent());
		assertEquals(1, ((SurfaceRealisation)mainAction.getActionValue("SayHi")).getSlots().size());
		assertEquals("name", ((SurfaceRealisation)mainAction.getActionValue("SayHi")).getSlots().get(0));
		assertTrue(mainAction.hasFeature("name"));
		
	}
	
	@Test
	public void initialStateExtraction() throws DialException {
		
		assertEquals (2,domain.getInitialState().getFluents().size());
		Fluent entity = domain.getInitialState().getFluents().get(1);
		log.debug("entity type: " + entity.getType().getName());
		
		assertEquals("robot", entity.getType().getName());
		log.info("label for state entity: " + entity.getLabel());
		assertTrue(entity.getValues().isEmpty());
		Fluent feat = entity.getFeatures().get(0);
		assertEquals (1,feat.getValues().size());		
		assertEquals ("Lenny",feat.getValues().firstKey());		
		assertEquals (1.0f,feat.getValues().get("Lenny"), 0.01f);
		
		Fluent variable = domain.getInitialState().getFluents().get(0);
		assertEquals("floor", variable.getType().getName());
		assertFalse(variable.getValues().isEmpty());
		assertEquals (1,variable.getValues().size());		
		assertEquals ("init",variable.getValues().firstKey());		
		assertEquals (1.0f,variable.getValues().get("init"), 0.01f);
	}
	
	
	@Test
	public void modelExtraction1() throws DialException {

		Model model = domain.getModel(Model.Type.USER_PREDICTION);
		
		assertEquals(2, model.getRules().size());
		Rule firstRule = model.getRules().get(0);
		
		assertTrue(firstRule.getInputVariables().isEmpty());
		assertEquals(2, firstRule.getOutputVariables().size());
		
		assertEquals("a_u", firstRule.getOutputVariable("a_u").getDenotation());
		assertEquals("a_u", firstRule.getOutputVariable("a_u").getType().getName());
		
		Case case1 = firstRule.getCases().get(0);
		
		assertTrue(case1.getCondition() instanceof VoidCondition);
		
		assertEquals(3, case1.getEffects().size());
		assertEquals(case1.getEffects().get(0).getProb(), 0.33f, 0.01f);
		assertTrue(((ComplexEffect)case1.getEffects().get(0)).getSubeffects().get(0) instanceof AssignEffect);
		assertEquals("a_u", ((AssignEffect)((ComplexEffect)case1.getEffects().get(0)).getSubeffects().get(0)).getVariable().getDenotation());
		assertEquals("a_u", ((AssignEffect)((ComplexEffect)case1.getEffects().get(0)).getSubeffects().get(0)).getVariable().getType().getName());
		assertEquals("AskFor", ((AssignEffect)((ComplexEffect)case1.getEffects().get(0)).getSubeffects().get(0)).getValue());
		assertEquals("AskFor", ((AssignEffect)((ComplexEffect)case1.getEffects().get(1)).getSubeffects().get(0)).getValue());
		
		Rule secondRule = model.getRules().get(1);
		assertEquals(3, secondRule.getInputVariables().size());
		assertEquals("i", secondRule.getInputVariable("i").getDenotation());
		assertEquals("intent", secondRule.getInputVariable("i").getType().getName());		
		assertEquals("a_m", secondRule.getInputVariable("a_m").getDenotation());
		
		assertEquals("a_u", firstRule.getOutputVariable("a_u").getDenotation());

		Iterator<Case> caseIt = secondRule.getCases().iterator();
		Case case2 = caseIt.next();
		
		assertTrue(case2.getCondition() instanceof ComplexCondition);
		assertEquals(3, ((ComplexCondition)case2.getCondition()).getSubconditions().size());
		assertTrue(((ComplexCondition)case2.getCondition()).getSubconditions().get(0) instanceof BasicCondition);
		assertEquals("i", ((BasicCondition)((ComplexCondition)case2.getCondition())
				.getSubconditions().get(0)).getVariable().getDenotation());
		assertEquals("Want", ((BasicCondition)((ComplexCondition)case2.getCondition())
				.getSubconditions().get(0)).getValue());
		assertEquals("a_m", ((BasicCondition)((ComplexCondition)case2.getCondition())
				.getSubconditions().get(2)).getVariable().getDenotation());
		assertEquals("AskRepeat", ((BasicCondition)((ComplexCondition)case2.getCondition())
				.getSubconditions().get(2)).getValue());
		
		assertEquals(1, case2.getEffects().size());
		assertEquals(case2.getEffects().get(0).getProb(), 1.0f, 0.01f);
		assertTrue(case2.getEffects().get(0) instanceof ComplexEffect);
		assertEquals("a_u", ((AssignEffect)((ComplexEffect)case2.getEffects().get(0)).getSubeffects().get(0)).getVariable().getDenotation());
		assertEquals("AskFor", ((AssignEffect)((ComplexEffect)case2.getEffects().get(0)).getSubeffects().get(0)).getValue());
		
		Case case3 = caseIt.next();
		assertEquals("Want", ((BasicCondition)((ComplexCondition)case3.getCondition())
				.getSubconditions().get(0)).getValue());
		assertEquals("AskFor", ((AssignEffect)((ComplexEffect)case3.getEffects().get(0)).getSubeffects().get(0)).getValue());

	}
	
	 
	
	@Test
	public void modelExtraction2() throws DialException {

		Model model = domain.getModel(Model.Type.SYSTEM_ACTIONVALUE);
		
		assertEquals(5, model.getRules().size());
		Rule firstRule = model.getRules().get(1);
		
		assertEquals(3, firstRule.getInputVariables().size());
		assertEquals("i", firstRule.getInputVariable("i").getDenotation());
		assertEquals(1, firstRule.getOutputVariables().size());
		assertEquals("a_m", firstRule.getOutputVariable("a_m").getDenotation());
		
		Case case1 = firstRule.getCases().get(0);
		assertTrue(case1.getCondition() instanceof ComplexCondition);
		assertEquals("i", ((BasicCondition)((ComplexCondition)case1.getCondition()).getSubconditions().get(1)).getVariable().getDenotation());
		assertEquals("Want", ((BasicCondition)((ComplexCondition)case1.getCondition()).getSubconditions().get(1)).getValue());
		
		assertEquals(1, case1.getEffects().size());
		assertEquals(case1.getEffects().get(0).getProb(), 1.0f, 0.01f);
		assertTrue(case1.getEffects().get(0) instanceof AssignEffect);
		assertEquals("a_m", ((AssignEffect)case1.getEffects().get(0)).getVariable().getDenotation());
		assertEquals("DoX", ((AssignEffect)case1.getEffects().get(0)).getValue());
		
		Case case2 = firstRule.getCases().get(1);
		assertTrue(case2.getCondition() instanceof ComplexCondition);
		assertEquals("i", ((BasicCondition)((ComplexCondition)case2.getCondition()).getSubconditions().get(1)).getVariable().getDenotation());
		assertEquals("Want", ((BasicCondition)((ComplexCondition)case2.getCondition()).getSubconditions().get(1)).getValue());

		assertEquals(1, case2.getEffects().size());
		assertEquals(case2.getEffects().get(0).getProb(), 0.0f, 0.01f);
		assertTrue(case2.getEffects().get(0) instanceof AssignEffect);
		assertEquals("a_m", ((AssignEffect)case2.getEffects().get(0)).getVariable().getDenotation());
		assertEquals("DoX", ((AssignEffect)case2.getEffects().get(0)).getValue());
	}
	
	
	@Test
	public void modelExtraction3 () throws DialException {

		Model model = domain.getModel(Model.Type.USER_REALISATION);
		
		Rule firstRule = model.getRules().get(0);
		
		assertEquals(2, firstRule.getInputVariables().size());
		assertEquals(1, firstRule.getOutputVariables().size());
		
		assertTrue(firstRule.getInputVariables().get(0) instanceof FixedVariable);
		assertTrue(firstRule.getInputVariables().get(1) instanceof FeatureVariable);
		assertEquals(((FeatureVariable)firstRule.getInputVariables().get(1)).getBaseVariable(), firstRule.getInputVariables().get(0));
	}
	
	@Test
	public void modelExtraction4() throws DialException {

		Model model = domain.getModel(Model.Type.SYSTEM_TRANSITION);
		
		assertEquals(2, model.getRules().size());
		
		Rule firstRule = model.getRules().get(0);
		
		assertEquals(2, firstRule.getInputVariables().size());
		assertEquals(1, firstRule.getOutputVariables().size());
		
		assertTrue(firstRule.getInputVariables().get(0) instanceof FixedVariable);
		assertTrue(firstRule.getInputVariables().get(1) instanceof EntityVariable);
		assertTrue(firstRule.getOutputVariables().get(0) instanceof PointerVariable);
		assertEquals(firstRule.getInputVariables().get(1), ((PointerVariable)firstRule.getOutputVariables().get(0)).getTarget());		
		
	}
	
	@Test 
	public void modelExtraction5() throws DialException {

		Model model = domain.getModel(Model.Type.USER_TRANSITION);
		
		assertEquals(3, model.getRules().size());
		
		Rule firstRule = model.getRules().get(0);
		Condition firstCond = firstRule.getCases().get(0).getCondition();
		assertTrue(firstCond instanceof ComplexCondition);
		assertEquals(3, ((ComplexCondition)firstCond).getSubconditions().size());
		assertEquals(BinaryOperator.AND, ((ComplexCondition)firstCond).getBinaryOperator());
		Condition subCondition1 = ((ComplexCondition)firstCond).getSubconditions().get(0);
		assertTrue(subCondition1 instanceof BasicCondition);
		assertEquals("a_u", ((BasicCondition)subCondition1).getVariable().getDenotation());
		assertEquals("AskFor", ((BasicCondition)subCondition1).getValue());		
	}
	
}