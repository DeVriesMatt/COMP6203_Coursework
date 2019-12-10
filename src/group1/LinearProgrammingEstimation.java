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
import scpsolver.constraints.LinearBiggerThanEqualsConstraint;
import scpsolver.constraints.LinearEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO: Redo commenting

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
        double[][] constraint1 = new double[num_variables + num_components*2 + 2][num_variables + num_components];
        double[] constraint2 = new double[num_variables + num_components*2 + 2];
        double[] objective3 = new double[num_variables + num_components];
        Arrays.fill(constraint2, 0.0D);
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
                    constraint1[position][cumulative_sum[iter] + issueDiscrete.getValueIndex(lower_bid.getValue(issueDiscrete).toString())] = -1.0D;
                    constraint1[position][cumulative_sum[iter] + issueDiscrete.getValueIndex(higher_bid.getValue(issueDiscrete).toString())] = 1.0D;

                }
            }
            constraint1[position][num_variables + position] = 1.0D;
            position++;
        }

        for (int i = 0; i < num_variables + num_components; i++){
            constraint1[position++][i] = 1.00D;
        }

        for (IssueDiscrete issueDiscrete: issueDiscreteList){
            int iter = issueDiscrete.getNumber() - 1;
            constraint1[position][cumulative_sum[iter] + issueDiscrete.getValueIndex(bidRanking.getMaximalBid().getValue(issueDiscrete).toString())] = 1.0D;
            constraint1[position+1][cumulative_sum[iter] + issueDiscrete.getValueIndex(bidRanking.getMinimalBid().getValue(issueDiscrete).toString())] = 1.0D;

        }
        constraint2[position] = highest_utility;
        constraint2[position + 1] = lowest_utility;

        LinearProgram linearProgram = new LinearProgram(objective3);

        for (int i = 0; i < constraint1.length - 2; i++){
            linearProgram.addConstraint(new LinearBiggerThanEqualsConstraint(constraint1[i], constraint2[i], ("c" + i + 1))); // Check empty string
        }

        linearProgram.addConstraint(new LinearEqualsConstraint(constraint1[constraint1.length - 2], constraint2[constraint2.length - 2], "c" + (constraint2.length - 1)));
        linearProgram.addConstraint(new LinearEqualsConstraint(constraint1[constraint1.length - 1], constraint2[constraint2.length - 1], "c" + (constraint2.length)));
        linearProgram.setMinProblem(true);
        LinearProgramSolver solver  = SolverFactory.newDefault();
        double[] optimality = solver.solve(linearProgram);


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
