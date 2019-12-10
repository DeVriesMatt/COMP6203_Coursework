package group1;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.issue.Issue;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.utility.EvaluatorDiscrete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OpponentModel {

    private EvaluatorDiscrete[] issuesList; // preference value of the issue list
    private int issueTotal; // the number issues in the domain
    private int[] issueIdList; // the id list of the issues

    private BidHistory historyBid;  // the opponents bidding history
    private List<Double> historyUtility; // values for the historical utility

    /**
     * Get issue list
     * @return issue list
     */
    public EvaluatorDiscrete[] getIssuesList() { return this.issuesList; }

    /**
     * Get total issues
     * @return issue list
     */
    public int getTotalIssues() { return this.issueTotal; }

    /**
     * Get issue ID list
     * @return issue ID list
     */
    public int[] getIssueIds() { return this.issueIdList; }

    public OpponentModel(Bid bid) {

        List<Issue> issueList = bid.getIssues();

        this.issueTotal = issueList.size();
        this.issueIdList = new int[this.issueTotal];

        this.historyBid = new BidHistory();
        this.historyUtility = new ArrayList<>();

        // Append the bid's issue number to the issueList
        for (int i = 0; i < getTotalIssues(); i++)
            this.issueIdList[i] = bid.getIssues().get(i).getNumber();;

        this.issuesList = new EvaluatorDiscrete[getTotalIssues()];

        // Generate evaluators for each issue
        for (int i = 0; i < getTotalIssues(); i++) {
            this.issuesList[i] = new EvaluatorDiscrete();
        }
    }

    /**
     * Add opponent's bid to its bid history
     */
    public void addBid(Bid bid) {

        this.historyBid.add(new BidDetails(bid, 0));
        this.setWeightValues();
    }

    /**
     * Set the weights of the issues based on the frequency
     */
    public void setWeightValues() {

        HashMap<ValueDiscrete, Double> frequencyValues = this.getWeightFreqAnalysis();
        int turns = this.historyBid.size(); // number of turns based on the size of the history

        double[] weightValue = new double[this.issueTotal]; // weight
        double weightValueTotal = 0.0; // Total weight

        for (int i = 0; i < this.issueTotal; i++) {

            // Get the frequency of the issues
            for (ValueDiscrete val : frequencyValues.keySet()) {
                try {

                    double freq = frequencyValues.get(val);

                    // The weight equals to:
                    weightValue[i] +=  Math.pow(freq, 2) / Math.pow(turns, 2);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Add weight value to the total weights
            weightValueTotal += weightValue[i];
        }

        // Normalised weight by dividing the weight value by the total weight value
        for (int i = 0; i < this.issueTotal; i++) {

            this.issuesList[i].setWeight(weightValue[i] / weightValueTotal); // set weight to normalised weights
        }
    }

    /**
     * Use frequency analysis to set the weights of each issues
     *
     * @return the hashmap containing the frequency to be used for weight computation
     */
    private HashMap<ValueDiscrete, Double> getWeightFreqAnalysis() {

        HashMap<ValueDiscrete, Double> issueValues = new HashMap<ValueDiscrete, Double>();

        // iterate over the issue list
        for (int i = 0; i < this.issueTotal; i++) {

            // Compute for the frequency of the issues's value found in the history bid
            for (int j = 0; j < this.historyBid.size(); j++) {

                // Get discrete value of the current issue in the bid history
                ValueDiscrete value = (ValueDiscrete) (this.historyBid.getHistory().get(j).getBid()
                        .getValue(this.issueIdList[i]));

                // Increment the frequency to one if the value of the issue already exists in the hash map
                if (issueValues.containsKey(value))
                    issueValues.put(value, issueValues.get(value) + 1);

                    // If not, add the value to the hash map and set it to 1
                else
                    issueValues.put(value, 1.0);
            }

            double maximum_frequency = 0.0;

            // Get the maximum frequency from the list of frequencies
            for (ValueDiscrete value : issueValues.keySet())
                if (issueValues.get(value) > maximum_frequency)
                    maximum_frequency = issueValues.get(value);

            // Set the issue's evaluation equal to the issues value divided by the maximum frequency
            for (ValueDiscrete value : issueValues.keySet()) {
                try {
                    getIssuesList()[i].setEvaluationDouble(value, issueValues.get(value) / maximum_frequency);
                } catch (Exception error) {
                    System.out.println(error.getMessage());
                }
            }
        }

        return issueValues;
    }

    /**
     * @return The array containing the number of times the value
     * of each issue changed.
     */
    private int[] getValueUpdateFrequency()
    {
        return getValueUpdateFrequency(this.historyBid.size());
    }

    /**
     * @return The array containing the number of times the value
     * of each issue changed given the number of turns.
     */
    private int[] getValueUpdateFrequency(int turns) {

        // The number of times the value of an issue has changed
        int[] freqUpdate = new int[this.issueTotal];

        for (int i = 0; i < this.issueTotal; i++) {

            Value currIssueValue = null; // Current issue value
            Value prevIssueVal = null; // Previous issue value

            int freqNumber = 0;

            int historyBidSize = this.historyBid.size()-1;
            int lastRound = this.historyBid.size() - turns - 1;

            // Given the number of turns, loop through the bidding history starting from the end
            for (int j = historyBidSize; j > lastRound; j--){
                currIssueValue = this.historyBid.getHistory().get(j).getBid().getValue(this.issueIdList[i]);

                // Increment frequency number if current value is not the same as the previous value.
                if (prevIssueVal != null && !prevIssueVal.equals(currIssueValue))  freqNumber++;

                prevIssueVal = currIssueValue;
            }
            freqUpdate[i] = freqNumber;
        }
        return freqUpdate;
    }

    /**
     * @param turns The number of rounds to be used
     * @return return range 0-1 on how stubborn an agent is
     * 1 - no updates in bids
     * 0 - all bids updated
     */
    public Double hardheaded(int turns)
    {
        // history size needs to be smaller than the given number of turns
        if (this.historyBid.size() < turns) return null;

        // get the frequency update of the issue values.
        int[] freqUpdate = this.getValueUpdateFrequency(turns);

        int totalUpdates = 0;
        for (int count: freqUpdate){
            totalUpdates += count;
        }

        double hardhead = 1 - (totalUpdates / (double) this.issueTotal) / (double)turns;

        return hardhead;
    }

    /**
     * Given the opponent's utility, compute for its estimate.
     *
     * @param bid The given bid used for the estimation
     * @return An approximation of the utility for the given bid
     */
    public double getOpponentUtility(Bid bid) {
        double util = 0.0; // utility of the bid

        HashMap<Integer, Value> bidVal = bid.getValues(); // bid values

        // Compute the total utility of the opponent's bid
        for (int i = 0; i < this.issueTotal; i++) {

            double weight = getIssuesList()[i].getWeight(); // weight of the current issue
            ValueDiscrete bidDiscreteValue = (ValueDiscrete) bidVal.get(this.issueIdList[i]); // discrete values

            if (( getIssuesList()[i]).getValues().contains(bidDiscreteValue)) {
                util += getIssuesList()[i].getDoubleValue(bidDiscreteValue) * weight; // preference of the discrete values
            }
        }
        return util;
    }

    /**
     * Append the utility history list to include the last utility
     * bid from the opponent.
     *
     * @param lastBidUtil
     */
    public void addUtilityHistory(double lastBidUtil) {
        this.historyUtility.add(lastBidUtil);
    }
}