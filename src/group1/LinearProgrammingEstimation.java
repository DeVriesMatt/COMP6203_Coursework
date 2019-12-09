package group1;

import agents.org.apache.commons.math.optimization.linear.*;
import agents.org.apache.commons.math.optimization.GoalType;
import agents.org.apache.commons.math.optimization.RealPointValuePair;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.OutcomeComparison;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LinearProgrammingEstimation {
    private BidRanking bidRanking;
    private AdditiveUtilitySpaceFactory additiveUtilitySpaceFactory;
    private List<IssueDiscrete> issueDiscreteList;

    public LinearProgrammingEstimation(){

    }

    public LinearProgrammingEstimation(Domain domain, BidRanking bids){
        bidRanking = bids;
        additiveUtilitySpaceFactory = new AdditiveUtilitySpaceFactory(domain);
        issueDiscreteList = additiveUtilitySpaceFactory.getIssues();
    }

    public AdditiveUtilitySpaceFactory Estimation()
            throws Exception{
        int issue_size = issueDiscreteList.size();
        int[] variable_num = new int[issue_size];
        int[] cumulative_sum = new int[issue_size + 1];
        int num_variables = 0;
        int num_components = bidRanking.getSize() - 1;
        double highest_utility = bidRanking.getHighUtility();
        double lowest_utility = bidRanking.getLowUtility();

        for (IssueDiscrete i : issueDiscreteList){
            int iter = i.getNumber() - 1;
            variable_num[iter] = i.getNumberOfValues();
            cumulative_sum[iter] = num_variables;
            num_variables += i.getNumberOfValues();
        }
        cumulative_sum[cumulative_sum.length - 1] = num_variables;
        double[][] constant1 = new double[num_variables + num_components*2 + 2][num_variables + num_components];
        double[] constant2 = new double[num_variables + num_components*2 + 2];
        double[] objective3 = new double[num_variables + num_components];
        Arrays.fill(constant2, 0.0D);
        Arrays.fill(objective3, 0.0D);
        for (int i = 0; i < num_components; i++){
            objective3[num_variables + i] = 1.0D;
        }
        int position = 0;
        // Perform a pairwise comparison
        for (OutcomeComparison outcomeComparison : bidRanking.getPairwiseComparisons()){
            Bid lower_bid = outcomeComparison.getBid1();
            Bid higher_bid = outcomeComparison.getBid2();

            for (IssueDiscrete issueDiscrete : issueDiscreteList){
                int iter = issueDiscrete.getNumber() - 1;
                if(!lower_bid.getValue(issueDiscrete).equals(higher_bid.getValue(issueDiscrete))){
                    constant1[position][cumulative_sum[iter] + issueDiscrete.getValueIndex(lower_bid.getValue(issueDiscrete).toString())] = -1.0D;
                    constant1[position][cumulative_sum[iter] + issueDiscrete.getValueIndex(higher_bid.getValue(issueDiscrete).toString())] = 1.0D;

                }
            }
            constant1[position][num_variables + position] = 1.0D;
            position++;
        }

        for (int i = 0; i < num_variables + num_components; i++){
            constant1[position++][i] = 1.00D;
        }

        for (IssueDiscrete issueDiscrete: issueDiscreteList){
            int iter = issueDiscrete.getNumber() - 1;
            constant1[position][cumulative_sum[iter] + issueDiscrete.getValueIndex(bidRanking.getMaximalBid().getValue(issueDiscrete).toString())] = 1.0D;
            constant1[position+1][cumulative_sum[iter] + issueDiscrete.getValueIndex(bidRanking.getMinimalBid().getValue(issueDiscrete).toString())] = 1.0D;

        }
        constant2[position] = highest_utility;
        constant2[position + 1] = lowest_utility;

        LinearObjectiveFunction linearObjectiveFunction = new LinearObjectiveFunction(objective3, 0.0D);
        List<LinearConstraint> linearConstraintList = new ArrayList<>();
        for (int i = 0; i < constant1.length - 2; i++){
            linearConstraintList.add(new LinearConstraint(constant1[i], Relationship.GEQ, constant2[i]));

        }
        linearConstraintList.add(new LinearConstraint(constant1[constant1.length - 2], Relationship.EQ, constant2[constant2.length - 2]));
        linearConstraintList.add(new LinearConstraint(constant1[constant1.length - 1], Relationship.EQ, constant2[constant2.length - 1]));

        SimplexSolver simplexSolver = new SimplexSolver();
        simplexSolver.setMaxIterations(2147483647);
        RealPointValuePair realPointValuePair = simplexSolver.optimize(linearObjectiveFunction, linearConstraintList, GoalType.MINIMIZE, false);

        double[] optimality = Arrays.copyOfRange(realPointValuePair.getPoint(), 0, num_variables);
        for (int i = 0; i < optimality.length; i++){
            optimality[i] = Math.max(0.0D, optimality[i]);
        }

        for (IssueDiscrete issueDiscrete : issueDiscreteList){
            int iter = issueDiscrete.getNumber() - 1;
            double max = 0.0D;
            double[] tmp = Arrays.copyOfRange(optimality, cumulative_sum[iter], cumulative_sum[iter+1]);

            for (double d : tmp){
                max = Math.max(d, max);
            }

            additiveUtilitySpaceFactory.setWeight(issueDiscrete, max);

            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()){
                additiveUtilitySpaceFactory.setUtility(issueDiscrete, valueDiscrete, tmp[issueDiscrete.getValueIndex(valueDiscrete)]);

            }
        }
        additiveUtilitySpaceFactory.normalizeWeights();

        return  additiveUtilitySpaceFactory;


    }
}
