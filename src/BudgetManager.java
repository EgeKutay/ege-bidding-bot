/**
 * Two-Phase Budget Manager with adaptive bidUnit feedback.
 *
 * Phase 1 (spent < 30% budget): Bid to win rounds + reach spend floor.
 *   - Adjusts bidUnit +1,1- each window based on win rate.
 *   - Too few wins -> raise bidUnit. Too many wins + PPE dropping → lower it.
 *
 * Phase 2 (spent >= 30% budget): Pure PPE optimization.
 *   - Adjusts bidUnit based on PPE trend only.
 *   - PPE dropping -> lower bidUnit. PPE rising -> hold or slight raise.
 */
public class BudgetManager {

    // ---- CONFIGURATION ----
    private static final double ESTIMATED_ROUNDS = 500_000; // Critical assumption. Sweet-spot for 1million or below total rounds. 
    private static final double SAFETY_FLOOR = 0.05;
    private static final double SPEND_TARGET_PCT = 0.30;   // 30% target
    private static final double INITIAL_BID_UNIT = 25.0;   // starting bidUnit
    private static final double MIN_BID_UNIT = 2.0;        // floor
    private static final double MAX_BID_UNIT = 60.0;       // ceiling
    // Phase 1 spend pacing targets (competitor-count independent) 
    private static final double PACE_LOW = 0.80;   // spending < 80% of target pace -> raise bidUnit
    private static final double PACE_HIGH = 1.20;  // spending > 120% of target pace -> lower bidUnit
    private static final int MAX_BID_CAP = 125;
    // Budget state
    private final long initialBudget;
    private long       remainingBudget;
    private long       totalSpent;
    private long       totalPointsWon;
    private int        roundsPlayed;
    private int        totalWins;

    // Adaptive bidUnit
    private double bidUnit;
    private final long spendTarget;
    private boolean phase2Active;

    // Per-window tracking (reset each S message)
    private int  windowRounds;
    private int  windowWins;
    private long windowEbucks;

    // PPE tracking for trend detection
    private double prevWindowPPE;
    private boolean hasPrevPPE;

    // Phase 2: lock bidUnit at entry and use gentle adjustments
    private double phase2BidUnit;

    // Consecutive window counters for smoothing decisions
    private int consecutiveZeroWins;
    private int consecutivePPEDrops;

    public BudgetManager(long initialBudget) {
        this.initialBudget   = initialBudget;
        this.remainingBudget = initialBudget;
        this.bidUnit         = INITIAL_BID_UNIT;
        this.spendTarget     = (long)(initialBudget * SPEND_TARGET_PCT);

    }

    // =====================================================================
    // RECORD KEEPING
    // =====================================================================

    public void recordRoundPlayed() {
        roundsPlayed++;
        windowRounds++;
    }

    public void recordWin(long ebucksSpent) {
        remainingBudget -= ebucksSpent;
        totalSpent      += ebucksSpent;
        totalWins++;
        windowWins++;
        windowEbucks += ebucksSpent;
    }

    // =====================================================================
    // BID CALCULATION
    // =====================================================================

    public int[] calculateBid(double score) {
        // Safety: Long-game strat always pay low if the game continues for a long time.
        long safeRemaining = remainingBudget - (long)(initialBudget * SAFETY_FLOOR);
        if (safeRemaining <= 0) {
            double rawMaxBid = score * 7; 
            int maxBid = Math.max(1, (int) Math.min(rawMaxBid, remainingBudget));
            return new int[]{1, maxBid};
        }

        // Phase transition check
        if (!phase2Active && totalSpent >= spendTarget) {
            phase2Active = true;
            phase2BidUnit = bidUnit; // carry over from Phase 1
            System.err.printf("[BUDGET] >>> PHASE 2 at round %d | spent=%d (%.1f%%) | pts=%d | score=%.4f | bidUnit=%.1f (locked)%n",
                roundsPlayed, totalSpent, (double) totalSpent / initialBudget * 100,
                totalPointsWon, totalSpent > 0 ? (double) totalPointsWon / totalSpent : 0, bidUnit);
        }

        if (phase2Active) {
            return calculatePhase2Bid(score, safeRemaining);
        } else {
            return calculatePhase1Bid(score, safeRemaining);
        }
    }



    /**
     * Phase 1: Bid with current adaptive bidUnit. We need wins + spending.
     * Auction: {1, maxBid} always start at 1 to win cheap when uncontested.
     */
    private int[] calculatePhase1Bid(double score, long safeRemaining) {
        double rawMaxBid = score * bidUnit;
        int maxBid = Math.max(1, (int) Math.min(rawMaxBid, Math.min(MAX_BID_CAP, safeRemaining)));
        return new int[]{1, maxBid};
    }

    /**
     * Phase 2: Bid with lower bidUnit to keep costs down.
     * No skip logic ascending auction means we only pay what we must.
     * Every cheap win improves our score. bidUnit controls max exposure.
     */
    private int[] calculatePhase2Bid(double score, long safeRemaining) {
        double rawMaxBid = score * bidUnit;
        int maxBid = Math.max(1, (int) Math.min(rawMaxBid, Math.min(MAX_BID_CAP, safeRemaining)));
        return new int[]{1, maxBid};
    }

    // =====================================================================
    // RECALIBRATION adjust bidUnit by flat +-1 every 100 rounds
    // =====================================================================

    public void recalibrate(long windowPoints, long windowEbucks) {
        totalPointsWon += windowPoints;

        double currentWindowPPE = this.windowEbucks > 0 ? (double) windowPoints / this.windowEbucks : 0;

        if (!phase2Active) {
            adjustPhase1(currentWindowPPE);
        } else {
            adjustPhase2(currentWindowPPE);
        }

        // Clamp bidUnit
        bidUnit = Math.max(MIN_BID_UNIT, Math.min(MAX_BID_UNIT, bidUnit));

        // Store for next window comparison
        prevWindowPPE = currentWindowPPE;
        hasPrevPPE = windowWins > 0; // only use as reference if we had wins

        // Reset per-window counters
        windowRounds = 0;
        windowWins = 0;
        this.windowEbucks = 0;
    }

    /**
     * Phase 1: flat +-1 bidUnit adjustment every 100 rounds.
     *
     * - Zero wins -> raise by 1 (too low to compete)
     * - Underspending (pacing < 0.8) -> raise by 1
     * - Overspending (pacing > 1.2) AND PPE dropping -> lower by 1
     * - Otherwise hold steady
     */
    private void adjustPhase1(double currentWindowPPE) {
        if (roundsPlayed < 200) return;

        // Zero wins is the clearest signal always raise
        if (windowWins == 0) {
            bidUnit += 1;
            return;
        }

        double spentFraction = (double) totalSpent / spendTarget;
        double gameFraction = (double) roundsPlayed / ESTIMATED_ROUNDS;
        double pacingRatio = gameFraction > 0 ? spentFraction / gameFraction : 1.0;

        if (pacingRatio < PACE_LOW) {
            bidUnit += 1;
        } else if (pacingRatio > PACE_HIGH && hasPrevPPE && currentWindowPPE < prevWindowPPE * 0.95) {
            bidUnit -= 1;
        }
    }

    /**
     * Phase 2: +-1 bidUnit, free-drifting.
     *
     * - 3 consecutive zero-win windows -> raise by 1
     * - 3 consecutive PPE drops -> lower by 1
     * - Otherwise hold
     */
    private void adjustPhase2(double currentWindowPPE) {
        if (windowWins == 0) {
            consecutiveZeroWins++;
            consecutivePPEDrops = 0;
        } else if (hasPrevPPE && currentWindowPPE < prevWindowPPE * 0.95) {
            consecutivePPEDrops++;
            consecutiveZeroWins = 0;
        } else {
            consecutiveZeroWins = 0;
            consecutivePPEDrops = 0;
        }

        if (consecutiveZeroWins >= 3 && bidUnit < phase2BidUnit) {
            bidUnit += 1;
            consecutiveZeroWins = 0;
        } else if (consecutivePPEDrops >= 3) {
            bidUnit -= 1;
            consecutivePPEDrops = 0;
        }
    }
}
