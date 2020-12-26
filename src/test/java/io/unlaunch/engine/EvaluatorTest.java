/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.unlaunch.engine;

import io.unlaunch.UnlaunchFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ghauri
 */
public class EvaluatorTest {

    private static final Logger logger = LoggerFactory.getLogger(EvaluatorTest.class);
    
    final String flagKey = "flag123";
    final String varKeyON = "ON";
    final String varKeyOFF = "OFF";
    final String generalVariation = "variation";
    
    Evaluator evaluator = Mockito.mock(Evaluator.class);
    FeatureFlag flag = Mockito.mock(FeatureFlag.class);
    UnlaunchUser percentRolloutUser = Mockito.mock(UnlaunchUser.class);

    @Before
    public void callBeforeEachTest() {
        setUpDefaultFlag(50L, 50L);
    }

    @Test
    public void testConstruction() {
        Evaluator eval = new Evaluator();
        assertNotNull(eval);
    }

    /**
     * Test of evaluate method, of class Evaluator.
     */
    @Test
    public void testEvaluateWhenUserIsInAllowlistForOnVariation() {
        logger.debug("EVALUATE_ALLOWLIST_USER_TREATMENT_ON");

        final String userId = "user123";
        
        UnlaunchUser user = Mockito.mock(UnlaunchUser.class);
        when(user.getId()).thenReturn(userId);

        UnlaunchFeature unlaunchFeature = UnlaunchFeature.create(flagKey, varKeyON, null, "");

        Evaluator instance = new Evaluator();
        UnlaunchFeature result = instance.evaluate(flag, user);

        assertEquals(unlaunchFeature.getVariation(), result.getVariation());
    }

    @Test
    public void testEvaluateWhenDefaultRuleIsServed() {
        logger.debug("EVALUATE_DEFAULT_RULE");

        final String userIdDefaultRule = "user456";
    
        UnlaunchUser user = Mockito.mock(UnlaunchUser.class);
        when(user.getId()).thenReturn(userIdDefaultRule);

        UnlaunchFeature unlaunchFeature = UnlaunchFeature.create(flagKey, generalVariation, null, "");

        Evaluator instance = new Evaluator();
        UnlaunchFeature result = instance.evaluate(flag, user);

        assertEquals(unlaunchFeature.getVariation(), result.getVariation());
    }

    @Test
    public void testPercentageRolloutDistribution() {
        logger.debug("EVALUATE_PERCENTAGE_ROLLOUT");

        Evaluator instance = new Evaluator();

        int countOFF = 0;
        int countON = 0;

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            String userPR = String.valueOf(i);
            when(percentRolloutUser.getId()).thenReturn(userPR);

            UnlaunchFeature result = instance.evaluate(flag, percentRolloutUser);

            if (result.getVariation().equals(varKeyOFF)) {
                countOFF++;
            }

            if (result.getVariation().equals(varKeyON)) {
                countON++;
            }
        }
        long totalTime = System.currentTimeMillis() - startTime;

        logger.info("countOFF was {}. Total time {} millis", countOFF, totalTime);

        assertTrue(countOFF < 600 && countOFF > 400);
        assertTrue(countON < 600 && countON > 400);
    }

    @Test
    public void testWhen_RolloutPercentageIsIncreased_Then_PreviouslyAssignedUsersKeepTheirOldVariation() {
        Map<String, String> userOnVariations = new HashMap<>();

        // Part 1: start with ON and 10 and store assigned variations
        setUpDefaultFlag(10, 90);
        Evaluator instance = new Evaluator();
        int countON = 0;
        for (int i = 0; i < 100; i++) {
            when(percentRolloutUser.getId()).thenReturn(String.valueOf(i));

            UnlaunchFeature result = instance.evaluate(flag, percentRolloutUser);

            if (result.getVariation().equals(varKeyON)) {
                countON++;
                userOnVariations.put(String.valueOf(i),result.getVariation() );
            }
        }
        assertTrue(countON >= 5 && countON < 20);
        // ---

        // Part 2:  increase percentage of ON; previously assigned users should keep their variation
        setUpDefaultFlag(40, 60);
        countON = 0;
        for (int i = 0; i < 100; i++) {
            when(percentRolloutUser.getId()).thenReturn(String.valueOf(i));

            UnlaunchFeature result = instance.evaluate(flag, percentRolloutUser);

            if (result.getVariation().equals(varKeyON)) {
                countON++;
                userOnVariations.put(String.valueOf(i),result.getVariation() );
            } else {
                if (userOnVariations.containsKey(String.valueOf(i))) {
                    Assert.fail("previously ON assigned user changed after %age of ON increased i.e. rolled out");
                }
            }
        }
        assertTrue("ON variation distribution was not as expected. " + countON, countON >= 30 && countON <= 50);
        // ---

        // Part 3: increase percentage of ON; previously assigned users should keep their variation
        setUpDefaultFlag(75, 25);
        countON = 0;
        for (int i = 0; i < 100; i++) {
            when(percentRolloutUser.getId()).thenReturn(String.valueOf(i));

            UnlaunchFeature result = instance.evaluate(flag, percentRolloutUser);

            if (result.getVariation().equals(varKeyON)) {
                countON++;
                userOnVariations.put(String.valueOf(i),result.getVariation() );
            } else {
                if (userOnVariations.containsKey(String.valueOf(i))) {
                    Assert.fail("previously ON assigned user changed after %age of ON increased i.e. rolled out");
                }
            }
        }
        assertTrue("ON variation distribution was not as expected. " + countON, countON >= 60 && countON <= 90);
        // ---
    }

    @Test
    @Ignore // On Umer's machine, 7-11 seconds
    public void testWhen_1000VariationsAreEvaluatedAgainstBucketing_Then_ItTakesLessThan15000MilliSeconds() {
        Evaluator instance = new Evaluator();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            when(percentRolloutUser.getId()).thenReturn(String.valueOf(i));
            instance.evaluate(flag, percentRolloutUser);
        }
        long totalTime = System.currentTimeMillis() - startTime;

        logger.info("Total time {} millis", totalTime);

        assertTrue(totalTime < 15000);
    }



    @Test
    public void testWhen_FlagIsDisabled_Then_DefaultVariationIsServed() {
        logger.debug("EVALUATE_FLAG_DISABLED_OFF_VARIATION_SERVED");

        when(flag.isEnabled()).thenReturn(Boolean.FALSE);
        UnlaunchFeature unlaunchFeature = UnlaunchFeature.create(flagKey, varKeyOFF, null, "");

        UnlaunchUser user = Mockito.mock(UnlaunchUser.class);
        when(user.getId()).thenReturn("40");

        Evaluator instance = new Evaluator();
        UnlaunchFeature result = instance.evaluate(flag, user);

        assertEquals(unlaunchFeature.getVariation(), result.getVariation());
    }

    private void setUpDefaultFlag(long percentageVariationON, long percentageVariationOFF) {
        evaluator = Mockito.mock(Evaluator.class);

        flag = Mockito.mock(FeatureFlag.class);
        when(flag.isEnabled()).thenReturn(true);
        when(flag.getKey()).thenReturn(flagKey);

        List<Rule> rules = new ArrayList<>();

        Rule rule = Mockito.mock(Rule.class);
        when(rule.isIsDefault()).thenReturn(Boolean.FALSE);

        Rule defaultRule = Mockito.mock(Rule.class);
        when(defaultRule.isIsDefault()).thenReturn(Boolean.TRUE);

        List<Condition> conditions = new ArrayList<>();
        Condition condition = Mockito.mock(Condition.class);

        when(condition.getAttribute()).thenReturn("gender");
        when(condition.getOperator()).thenReturn(Operator.EQUALS);
        when(condition.getType()).thenReturn(AttributeType.STRING);

        List<String> values = new ArrayList<>();
        values.add("male");

        when(condition.getValues()).thenReturn(values);

        conditions.add(condition);

        when(rule.getConditions()).thenReturn(conditions);

        Variation variationON = Mockito.mock(Variation.class);
        Variation variationOFF = Mockito.mock(Variation.class);
        Variation defaultVariation = Mockito.mock(Variation.class);

        final String allowList = "user123";

        when(variationON.getAllowList()).thenReturn(allowList);

        when(variationON.getKey()).thenReturn(varKeyON);
        when(variationON.getRolloutPercentage()).thenReturn(percentageVariationON);

        when(variationOFF.getKey()).thenReturn(varKeyOFF);
        when(variationOFF.getRolloutPercentage()).thenReturn(percentageVariationOFF);

        when(defaultVariation.getKey()).thenReturn(generalVariation);
        when(defaultVariation.getRolloutPercentage()).thenReturn(100L);

        List<Variation> variations = new ArrayList<>();
        variations.add(variationON);
        variations.add(variationOFF);

        when(rule.getVariations()).thenReturn(variations);

        List<Variation> defaultRuleVariations = new ArrayList<>();
        defaultRuleVariations.add(defaultVariation);

        when(defaultRule.getVariations()).thenReturn(defaultRuleVariations);

        rules.add(rule);
        rules.add(defaultRule);

        when(flag.getRules()).thenReturn(rules);
        when(flag.getDefaultRule()).thenReturn(defaultRule);

        when(flag.getVariations()).thenReturn(variations);
        when(flag.getOffVariation()).thenReturn(variationOFF);

        percentRolloutUser = Mockito.mock(UnlaunchUser.class);
        when(rule.matches(percentRolloutUser)).thenReturn(true);

        UnlaunchStringValue stringValue = Mockito.mock(UnlaunchStringValue.class);
        when(stringValue.get()).thenReturn("male");

        Map<String, UnlaunchValue> map = new HashMap();
        map.put("gender", stringValue);

        when(percentRolloutUser.getAllAttributes()).thenReturn(map);
    }

}
