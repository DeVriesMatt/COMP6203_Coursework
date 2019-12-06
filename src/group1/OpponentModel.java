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
    public int[] getIssueIds() {
        return this.issueIdList;
    }

    public OpponentModel(Bid bid) {

        List<Issue> issueList = bid.getIssues();

        this.issueTotal = issueList.size();
        this.issueIdList = new int[this.issueTotal];

        this.historyBid = new BidHistory();
        this.historyUtility = new ArrayList<>();

        for (int i = 0; i < getTotalIssues(); i++) {
            int issueID = bid.getIssues().get(i).getNumber();
            getIssueIds()[i] = issueID;
        }

        this.issuesList = new EvaluatorDiscrete[getTotalIssues()];
        for (int i = 0; i < getTotalIssues(); i++) {
            this.issuesList[i] = new EvaluatorDiscrete();
        }
    }

    /**
     * Add bid to bid history of opponent
     */
    public void addBid(Bid bid) {

        this.historyBid.add(new BidDetails(bid, 0));
        this.setWeightValues();
    }

    /**
     * Set issue weights and values
     */
    public void setWeightValues() {

        HashMap<ValueDiscrete, Double> weightValues = this.setWeightFreqAnalysis();

        int turns = this.historyBid.size();

        double[] w = new double[this.issueTotal];
        double weightTotal = 0.0;

        for (int i = 0; i < this.issueTotal; i++) {

            // get frequency
            for (ValueDiscrete val : weightValues.keySet()) {
                try {

                    double freq = weightValues.get(val);
                    w[i] +=  Math.pow(freq, 2) / Math.pow(turns, 2);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            weightTotal += w[i];
        }

        for (int i = 0; i < issueTotal; i++) {

            double normalizedWeight = w[i] / weightTotal;
            this.issuesList[i].setWeight(normalizedWeight);
        }

    }

    /**
     * Use the frequency analysis to set the weights of each issues
     *
     * @return issue values
     */
    private HashMap<ValueDiscrete, Double> setWeightFreqAnalysis() {

        HashMap<ValueDiscrete, Double> issueValues = new HashMap<ValueDiscrete, Double>();

        for (int i = 0; i < this.issueTotal; i++) {

            // Compute for the frequency of the issues
            for (int j = 0; j < this.historyBid.size(); j++) {

                ValueDiscrete value = (ValueDiscrete) (this.historyBid.getHistory().get(j).getBid()
                        .getValue(this.issueIdList[i]));

                if (issueValues.containsKey(value)) issueValues.put(value, issueValues.get(value) + 1);

                else issueValues.put(value, 1.0);
            }

            // Take the greatest number of times issue value was used
            double frequency = 0.0;
            for (ValueDiscrete value : issueValues.keySet())
                if (issueValues.get(value) > frequency) frequency = issueValues.get(value);

            for (ValueDiscrete value : issueValues.keySet()) {
                try {

                    int rounds = this.historyBid.size();

                    // Set evaluation to number of times the issue was used divided by the maximum times it was used
                    getIssuesList()[i].setEvaluationDouble(value, issueValues.get(value) / frequency);
                } catch (Exception error) {
                    System.out.println(error.getMessage());
                }
            }
        }

        return issueValues;
    }

    /**
     * @return the issues frequency update
     */
    private int[] getIssueUpdates()
    {
        return getIssueUpdates(this.historyBid.size());
    }

    /**
     * @return the issues frequency update
     */
    private int[] getIssueUpdates(int rounds) {

        int[] freqUpdate = new int[this.issueTotal]; // the frequency number of each issue

        for (int i = 0; i < this.issueTotal; i++) {

            Value currVal = null;
            Value prevRoundVal = null;

            int freqNumber = 0;

            int historyBidSize = this.historyBid.size()-1;
            int lastRoundBids = this.historyBid.size() - rounds - 1;

            // From the bidding history loop through the number of given rounds in reverse
            for (int j = historyBidSize; j > lastRoundBids; j--){
                currVal = this.historyBid.getHistory().get(j).getBid().getValue(this.issueIdList[i]);

                // Increment frequency number if current value is not the same as the previous value.
                if (prevRoundVal != null && !prevRoundVal.equals(currVal))  freqNumber++;

                prevRoundVal = currVal;
            }
            freqUpdate[i] = freqNumber;
        }
        return freqUpdate;
    }

    /**
     * @param rounds The number of rounds to be used
     * @return 0 for an update in previous turn and 1 for zero changes in bids
     */
    public Double hardheaded(int rounds)
    {
        // historyBid size must be at least equal to round n#
        if (this.historyBid.size() < rounds) return null;

        int[] freqUpdate = this.getIssueUpdates(rounds);

        int total = 0;
        for (int count: freqUpdate){
            total += count;
        }

        double hardhead = 1 - (total / (double) this.issueTotal) / (double)rounds;

        // 1 = hardheaded
        // 0 = give up
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

            double weight = getIssuesList()[i].getWeight(); // weight of the issue
            ValueDiscrete value = (ValueDiscrete) bidVal.get(this.issueIdList[i]); // discrete values

            if (( getIssuesList()[i]).getValues().contains(value)) {
                util += getIssuesList()[i].getDoubleValue(value) * weight; // preference of the discrete values
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